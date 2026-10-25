import {NextResponse} from 'next/server';
import {authorizedKey,hasScope,readData} from '../../../../../../../lib/sms';
import {firebaseDb} from '../../../../../../../lib/firebase-admin';

export async function GET(req:Request,{params}:{params:Promise<{id:string}>}){
  try{
    const key=authorizedKey(req); const data=await readData(); const apiKey=data.apiKeys.find((k:any)=>k.token===key&&k.enabled!==false);
    if(!apiKey||!(hasScope(apiKey,'app_messages:send')||hasScope(apiKey,'messages:send')))return NextResponse.json({ok:false,error:'Unauthorized'},{status:401});
    const {id}=await params;
    if(!id)return NextResponse.json({ok:false,error:'Message id is required'},{status:400});
    const snap=await firebaseDb().collectionGroup('messages').where('id','==',id).limit(1).get();
    if(snap.empty)return NextResponse.json({ok:false,error:'Message not found'},{status:404});
    const d=snap.docs[0],m=d.data();
    return NextResponse.json({ok:true,data:{id:d.id,status:Number(m.readAt||0)>0?'read':'sent',sender_id:m.senderId||'',sender_name:m.senderName||'',recipient_id:m.recipientId||'',message:m.text||'',created_at:m.createdAt||0,read_at:m.readAt||null,conversation_id:d.ref.parent.parent?.id||''}});
  }catch(e:any){return NextResponse.json({ok:false,error:e?.message||'Status lookup failed'},{status:500});}
}
