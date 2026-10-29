import {NextResponse} from 'next/server';
import crypto from 'crypto';
import {authorizedKey,hasScope,readData,countryFromNumber,normalizeNumber} from '../../../../../lib/sms';
import {firebaseDb,firebaseMessaging} from '../../../../../lib/firebase-admin';

function uidOf(id:string){return 'app:'+crypto.createHash('sha256').update(id).digest('hex').slice(0,40)}
function conversationId(a:string,b:string){return [a,b].sort().join('_')}

export async function POST(req:Request){
  try{
    const b=await req.json().catch(()=>({}));
    const installation=String(b.installation_id||'').trim();
    const recipientUid=String(b.recipient_uid||'').trim();
    const text=String(b.message??b.text??'').trim();

    if(installation){
      if(installation.length<32||!recipientUid||!text)return NextResponse.json({ok:false,error:'Invalid message request'},{status:400});
      if(text.length>4000)return NextResponse.json({ok:false,error:'Message is too long'},{status:400});
      const senderUid=uidOf(installation),db=firebaseDb();
      const [ss,rs]=await Promise.all([db.collection('profiles').doc(senderUid).get(),db.collection('profiles').doc(recipientUid).get()]);
      if(!ss.exists)return NextResponse.json({ok:false,error:'Messages SOM account is not registered'},{status:401});
      if(!rs.exists||rs.data()?.enabled===false)return NextResponse.json({ok:false,error:'Messages SOM user not found'},{status:404});
      const sender=ss.data()!,recipient=rs.data()!;
      if(sender.enabled===false)return NextResponse.json({ok:false,error:'Your Messages SOM account is disabled'},{status:403});
      const token=String(recipient.fcmToken||'').trim();
      if(!token)return NextResponse.json({ok:false,error:'Recipient is not currently reachable'},{status:503});
      const conversation=conversationId(senderUid,recipientUid),conversationRef=db.collection('appConversations').doc(conversation),ref=conversationRef.collection('messages').doc(),now=Date.now();
      const msg={id:ref.id,senderUid,senderId:sender.messagesId||'',senderName:sender.displayName||'Messages SOM user',recipientUid,recipientId:recipient.messagesId||'',text,createdAt:now,deliveredAt:0,readAt:0,deleted:false,source:'messages_som',allowReplies:true};
      await db.runTransaction(async tx=>{tx.set(ref,msg);tx.set(conversationRef,{participants:[senderUid,recipientUid],lastText:text,lastAt:now,lastSenderUid:senderUid,updatedAt:now},{merge:true})});
      try{
        await firebaseMessaging().send({token,data:{type:'app_message',title:sender.displayName||sender.messagesId||'Messages SOM',body:text.slice(0,180),conversationId:conversation,messageId:ref.id,senderUid,senderId:sender.messagesId||'',senderName:sender.displayName||'',allowReplies:'true'},android:{priority:'high'}});
        await ref.update({deliveredAt:Date.now()});
        return NextResponse.json({ok:true,data:{id:ref.id,status:'delivered'}})
      }catch(e:any){return NextResponse.json({ok:false,error:'Message notification delivery failed',messageId:ref.id},{status:502})}
    }

    const key=authorizedKey(req),data=await readData();
    const configuredInternal=Boolean(process.env.APP_API_KEY&&key===process.env.APP_API_KEY);
    const apiKey=data.apiKeys.find((k:any)=>k.token===key&&k.enabled!==false);
    if(!configuredInternal&&(!apiKey||!(hasScope(apiKey,'app_messages:send')||hasScope(apiKey,'messages:send'))))return NextResponse.json({ok:false,error:'Unauthorized'},{status:401});
    if(data.settings?.appMessaging===false)return NextResponse.json({ok:false,error:'Messages SOM app messaging is disabled'},{status:503});
    const senderValue=String(b.sender_id||'').trim(),to=normalizeNumber(b.to);
    if(!senderValue||!to||!text)return NextResponse.json({ok:false,error:'sender_id, to and message are required'},{status:400});
    if(!/^\+?[1-9]\d{6,14}$/.test(to))return NextResponse.json({ok:false,error:'Invalid international recipient number'},{status:400});
    if(text.length>4000)return NextResponse.json({ok:false,error:'Message is too long (maximum 4000 characters)'},{status:400});
    const sender=data.senderIds.find((s:any)=>s.enabled!==false&&(s.id===senderValue||s.providerSenderId===senderValue||s.name===senderValue));
    if(!sender)return NextResponse.json({ok:false,error:'Sender ID not found or disabled'},{status:400});
    if(sender.appMessagingEnabled===false)return NextResponse.json({ok:false,error:'Sender ID is not enabled for Messages SOM'},{status:403});
    const country=countryFromNumber(to),allowed=Array.isArray(sender.countries)?sender.countries.map((x:string)=>x.toUpperCase()).filter(Boolean):[];
    if(allowed.length&&country&&!allowed.includes(country))return NextResponse.json({ok:false,error:`Sender ID is not enabled for ${country}`},{status:403});
    const db=firebaseDb(),users=await db.collection('profiles').where('phoneNumber','==',to).limit(2).get();
    if(users.empty)return NextResponse.json({ok:false,error:'User does not have Messages SOM'},{status:404});
    if(users.size>1)return NextResponse.json({ok:false,error:'Recipient number is linked to multiple Messages SOM accounts'},{status:409});
    const recipientDoc=users.docs[0],recipient=recipientDoc.data();
    if(recipient.enabled===false)return NextResponse.json({ok:false,error:'Recipient Messages SOM account is disabled'},{status:403});
    if(!String(recipient.fcmToken||'').trim())return NextResponse.json({ok:false,error:'Recipient is not currently reachable'},{status:503});
    const senderUid=`api:${apiKey?.id||'internal'}`,externalRecipientUid=recipientDoc.id,externalSenderId=String(sender.providerSenderId||sender.name||senderValue),externalSenderName=String(sender.name||senderValue),allowReplies=sender.allowReplies===true,conversation=conversationId(senderUid,externalRecipientUid),messageRef=db.collection('appConversations').doc(conversation).collection('messages').doc(),now=Date.now();
    const message={id:messageRef.id,senderUid,senderId:externalSenderId,senderName:externalSenderName,recipientUid:externalRecipientUid,recipientId:recipient.messagesId||'',text,createdAt:now,deliveredAt:0,readAt:0,deleted:false,source:'external_app',allowReplies};
    const conversationRef=db.collection('appConversations').doc(conversation),senderProfileRef=db.collection('profiles').doc(senderUid);
    await db.runTransaction(async tx=>{tx.set(senderProfileRef,{uid:senderUid,messagesId:externalSenderId,displayName:externalSenderName,phoneNumber:'',photoUrl:'',fcmToken:'',enabled:true,allowReplies,updatedAt:now},{merge:true});tx.set(messageRef,message);tx.set(conversationRef,{participants:[senderUid,externalRecipientUid],externalSenderId,lastText:text,lastAt:now,lastSenderUid:senderUid,updatedAt:now},{merge:true})});
    return NextResponse.json({ok:true,data:{id:messageRef.id,status:'sent',notification:'queued_for_fcm',recipient:recipient.messagesId||null,sender_id:externalSenderId,allow_replies:allowReplies}});
  }catch(e:any){return NextResponse.json({ok:false,error:e?.message||'App message failed'},{status:500})}
}
