import {NextResponse} from 'next/server';
import {authorizedKey,hasScope,readData,countryFromNumber,normalizeNumber} from '../../../../../lib/sms';
import {firebaseDb,firebaseMessaging} from '../../../../../lib/firebase-admin';

export async function POST(req:Request){
  try{
    const key=authorizedKey(req);const data=await readData();
    const configuredInternal=Boolean(process.env.APP_API_KEY&&key===process.env.APP_API_KEY);
    const apiKey=data.apiKeys.find((k:any)=>k.token===key&&k.enabled!==false);
    if(!configuredInternal&&(!apiKey||!(hasScope(apiKey,'app_messages:send')||hasScope(apiKey,'messages:send'))))return NextResponse.json({ok:false,error:'Unauthorized'},{status:401});
    if(data.settings?.appMessaging===false)return NextResponse.json({ok:false,error:'Messages SOM app messaging is disabled'},{status:503});
    const body=await req.json().catch(()=>({}));const senderValue=String(body.sender_id||'').trim();const to=normalizeNumber(body.to);const text=String(body.message??body.text??'').trim();
    if(!senderValue||!to||!text)return NextResponse.json({ok:false,error:'sender_id, to and message are required'},{status:400});
    if(!/^\+?[1-9]\d{6,14}$/.test(to))return NextResponse.json({ok:false,error:'Invalid international recipient number'},{status:400});
    if(text.length>4000)return NextResponse.json({ok:false,error:'Message is too long (maximum 4000 characters)'},{status:400});
    const sender=data.senderIds.find((s:any)=>s.enabled!==false&&(s.id===senderValue||s.providerSenderId===senderValue||s.name===senderValue));
    if(!sender)return NextResponse.json({ok:false,error:'Sender ID not found or disabled'},{status:400});
    if(sender.appMessagingEnabled===false)return NextResponse.json({ok:false,error:'Sender ID is not enabled for Messages SOM'},{status:403});
    const country=countryFromNumber(to);const allowed=Array.isArray(sender.countries)?sender.countries.map((x:string)=>x.toUpperCase()).filter(Boolean):[];
    if(allowed.length&&country&&!allowed.includes(country))return NextResponse.json({ok:false,error:`Sender ID is not enabled for ${country}`},{status:403});
    const db=firebaseDb();const users=await db.collection('profiles').where('phoneNumber','==',to).limit(2).get();
    if(users.empty)return NextResponse.json({ok:false,error:'User does not have Messages SOM'},{status:404});
    if(users.size>1)return NextResponse.json({ok:false,error:'Recipient number is linked to multiple Messages SOM accounts'},{status:409});
    const recipientDoc=users.docs[0];const recipient=recipientDoc.data();
    if(recipient.enabled===false)return NextResponse.json({ok:false,error:'Recipient Messages SOM account is disabled'},{status:403});
    const fcmToken=String(recipient.fcmToken||'').trim();
    if(!fcmToken)return NextResponse.json({ok:false,error:'Recipient has no active Messages SOM notification token'},{status:503});
    const senderUid=`api:${apiKey?.id||'internal'}`;const recipientUid=recipientDoc.id;const externalSenderId=String(sender.providerSenderId||sender.name||senderValue);const externalSenderName=String(sender.name||senderValue);const allowReplies=sender.allowReplies===true;const conversation=conversationId(senderUid,recipientUid);const messageRef=db.collection('appConversations').doc(conversation).collection('messages').doc();const now=Date.now();
    const message={id:messageRef.id,senderUid,senderId:externalSenderId,senderName:externalSenderName,recipientUid,recipientId:recipient.messagesId||'',text,createdAt:now,deliveredAt:0,readAt:0,deleted:false,source:'external_app',allowReplies};
    const conversationRef=db.collection('appConversations').doc(conversation);const senderProfileRef=db.collection('profiles').doc(senderUid);
    await db.runTransaction(async tx=>{tx.set(senderProfileRef,{uid:senderUid,messagesId:externalSenderId,displayName:externalSenderName,phoneNumber:'',photoUrl:'',fcmToken:'',enabled:true,allowReplies,updatedAt:now},{merge:true});tx.set(messageRef,message);tx.set(conversationRef,{participants:[senderUid,recipientUid],externalSenderId,lastText:text,lastAt:now,lastSenderUid:senderUid,updatedAt:now},{merge:true});});
    try{
      await firebaseMessaging().send({
        token:fcmToken,
        notification:{title:externalSenderName,body:text.slice(0,180)},
        data:{type:'app_message',conversationId:conversation,messageId:messageRef.id,senderUid,senderId:externalSenderId,senderName:externalSenderName,allowReplies:String(allowReplies)},
        android:{priority:'high',notification:{channelId:'chat'}}
      });
      await messageRef.update({deliveredAt:Date.now()});
      return NextResponse.json({ok:true,data:{id:messageRef.id,status:'delivered',recipient:recipient.messagesId||null,sender_id:externalSenderId,allow_replies:allowReplies}});
    }catch(error:any){
      const code=String(error?.code||'');
      if(code.includes('registration-token-not-registered')||code.includes('invalid-registration-token'))await recipientDoc.ref.update({fcmToken:''});
      return NextResponse.json({ok:false,error:'Messages SOM notification delivery failed',messageId:messageRef.id},{status:502});
    }
  }catch(e:any){return NextResponse.json({ok:false,error:e?.message||'App message failed'},{status:500});}
}
function conversationId(a:string,b:string){return [a,b].sort().join('_');}
