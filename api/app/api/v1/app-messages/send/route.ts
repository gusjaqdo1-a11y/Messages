import {NextResponse} from 'next/server';
import crypto from 'crypto';
import {firebaseDb,firebaseMessaging} from '../../../../../lib/firebase-admin';
function uidOf(id:string){return 'app:'+crypto.createHash('sha256').update(id).digest('hex').slice(0,40)}
export async function POST(req:Request){try{
 const b=await req.json().catch(()=>({}));const installation=String(b.installation_id||'').trim();const recipientUid=String(b.recipient_uid||'').trim();const text=String(b.message||'').trim();
 if(installation.length<32||!recipientUid||!text)return NextResponse.json({ok:false,error:'Invalid message request'},{status:400});if(text.length>4000)return NextResponse.json({ok:false,error:'Message is too long'},{status:400});
 const senderUid=uidOf(installation),db=firebaseDb(),senderRef=db.collection('profiles').doc(senderUid),recipientRef=db.collection('profiles').doc(recipientUid);const [ss,rs]=await Promise.all([senderRef.get(),recipientRef.get()]);
 if(!ss.exists)return NextResponse.json({ok:false,error:'Messages SOM account is not registered'},{status:401});if(!rs.exists||rs.data()?.enabled===false)return NextResponse.json({ok:false,error:'Messages SOM user not found'},{status:404});
 const sender=ss.data()!,recipient=rs.data()!;if(!sender.enabled)return NextResponse.json({ok:false,error:'Your Messages SOM account is disabled'},{status:403});
 const token=String(recipient.fcmToken||'').trim();if(!token)return NextResponse.json({ok:false,error:'Recipient is not currently reachable'},{status:503});
 const conversation=[senderUid,recipientUid].sort().join('_'),ref=db.collection('appConversations').doc(conversation).collection('messages').doc(),now=Date.now();
 const msg={id:ref.id,senderUid,senderId:sender.messagesId||'',senderName:sender.displayName||'Messages SOM user',recipientUid,recipientId:recipient.messagesId||'',text,createdAt:now,deliveredAt:0,readAt:0,deleted:false,source:'messages_som'};
 await db.runTransaction(async tx=>{tx.set(ref,msg);tx.set(db.collection('appConversations').doc(conversation),{participants:[senderUid,recipientUid],lastText:text,lastAt:now,lastSenderUid:senderUid,updatedAt:now},{merge:true})});
 try{await firebaseMessaging().send({token,data:{type:'app_message',title:sender.displayName||sender.messagesId||'Messages SOM',body:text.slice(0,180),conversationId:conversation,messageId:ref.id,senderUid,senderId:sender.messagesId||'',senderName:sender.displayName||'',allowReplies:'true'},android:{priority:'high'}});await ref.update({deliveredAt:Date.now()});return NextResponse.json({ok:true,data:{id:ref.id,status:'delivered'}})}catch(e:any){return NextResponse.json({ok:false,error:'Message notification delivery failed',messageId:ref.id},{status:502})}
}catch(e:any){return NextResponse.json({ok:false,error:e?.message||'Send failed'},{status:500})}}
