import {cert,getApps,initializeApp} from 'firebase-admin/app';
import {FieldValue,getFirestore} from 'firebase-admin/firestore';
import {authorizedKey,hasScope,normalizeNumber,readData} from './sms';

function firebaseApp(){
  if(getApps().length)return getApps()[0];
  const json=process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if(json){return initializeApp({credential:cert(JSON.parse(json))});}
  const projectId=process.env.FIREBASE_PROJECT_ID;
  const clientEmail=process.env.FIREBASE_CLIENT_EMAIL;
  const privateKey=process.env.FIREBASE_PRIVATE_KEY?.replace(/\\n/g,'\n');
  if(!projectId||!clientEmail||!privateKey)throw new Error('Firebase Admin credentials are not configured');
  return initializeApp({credential:cert({projectId,clientEmail,privateKey})});
}

const db=()=>getFirestore(firebaseApp());
const conversationId=(a:string,b:string)=>[a,b].sort().join('_');
const validPhone=(v:string)=>/^\+?[1-9]\d{6,14}$/.test(v);

export type AppSendResult={id:string;status:'sent';senderId:string;to:string;recipientUid:string;conversationId:string};

export async function sendAppMessage(req:Request,body:any):Promise<AppSendResult>{
  const key=authorizedKey(req);
  const data=await readData();
  const apiKey=data.apiKeys.find((k:any)=>k.token===key&&k.enabled!==false);
  if(!apiKey||!hasScope(apiKey,'messages:send'))throw new Error('Unauthorized');
  if(data.settings?.appMessaging===false)throw new Error('Messages SOM app messaging is disabled');

  const senderValue=String(body?.sender_id||'').trim();
  const to=normalizeNumber(String(body?.to||''));
  const text=String(body?.message??body?.text??'').trim();
  if(!senderValue||!to||!text)throw new Error('sender_id, to and message are required');
  if(!validPhone(to))throw new Error('Use an international number, e.g. +2519XXXXXXXX');
  if(text.length>4000)throw new Error('Message is too long (maximum 4000 characters)');

  const sender=data.senderIds.find((s:any)=>s.enabled!==false&&(s.id===senderValue||s.providerSenderId===senderValue||s.name===senderValue));
  if(!sender)throw new Error('Sender ID not found or disabled');
  if(sender.appMessagingEnabled===false)throw new Error('This Sender ID is not enabled for Messages SOM');

  const allowed=Array.isArray(sender.countries)?sender.countries.map((x:string)=>x.toUpperCase()).filter(Boolean):[];
  if(allowed.length){
    const countryPrefixes:any={ET:'251',SO:'252',KE:'254',UG:'256',TZ:'255',DJ:'253',SD:'249',RW:'250',NG:'234',GH:'233',ZA:'27',GB:'44',DE:'49',SE:'46',US:'1',CA:'1',AE:'971',SA:'966',EG:'20',FR:'33',IT:'39',NL:'31',BE:'32',NO:'47',DK:'45',FI:'358',ES:'34',PT:'351',IN:'91',PK:'92',BD:'880'};
    const country=Object.entries(countryPrefixes).find(([,p])=>to.replace('+','').startsWith(String(p)))?.[0]||'';
    if(country&&!allowed.includes(country))throw new Error(`Sender ID is not enabled for ${country}`);
  }

  const firestore=db();
  const matches=await firestore.collection('profiles').where('phoneNumber','==',to).limit(1).get();
  if(matches.empty)throw new Error('User does not have Messages SOM');
  const recipientDoc=matches.docs[0];
  const recipient=recipientDoc.data();
  if(recipient.enabled===false)throw new Error('Recipient Messages SOM account is disabled');
  if(!recipient.fcmToken)throw new Error('Recipient Messages SOM is not ready for notifications');

  const externalUid=`external:${String(sender.id)}`;
  const cid=conversationId(externalUid,recipientDoc.id);
  const messageRef=firestore.collection('appConversations').doc(cid).collection('messages').doc();
  const now=Date.now();
  const senderName=String(sender.providerSenderId||sender.name||senderValue).toUpperCase();
  const message={
    id:messageRef.id,senderUid:externalUid,senderId:senderName,senderName,
    recipientUid:recipientDoc.id,recipientId:String(recipient.messagesId||''),text,
    createdAt:now,deliveredAt:0,readAt:0,deleted:false,source:'external_app',
    externalAppId:String(apiKey.id||apiKey.name||'api'),senderRecordId:String(sender.id)
  };
  const convRef=firestore.collection('appConversations').doc(cid);
  const batch=firestore.batch();
  batch.set(messageRef,message);
  batch.set(convRef,{participants:[externalUid,recipientDoc.id],lastText:text,lastAt:now,lastSenderUid:externalUid,senderId:senderName,source:'external_app',updatedAt:now},{merge:true});
  await batch.commit();

  return {id:messageRef.id,status:'sent',senderId:senderName,to,recipientUid:recipientDoc.id,conversationId:cid};
}

export async function getAppMessage(id:string){
  const firestore=db();
  const snap=await firestore.collectionGroup('messages').where('id','==',id).limit(1).get();
  if(snap.empty)return null;
  const d=snap.docs[0];
  const x=d.data();
  return {id:d.id,status:x.readAt&&Number(x.readAt)>0?'read':'sent',senderId:x.senderId||'',senderName:x.senderName||'',recipientId:x.recipientId||'',text:x.text||'',createdAt:x.createdAt||0,readAt:x.readAt||0,conversationId:d.ref.parent.parent?.id||''};
}
