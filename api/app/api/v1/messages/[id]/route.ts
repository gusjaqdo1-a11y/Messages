import { NextResponse } from 'next/server';
import { authorizedKey, hasScope, readData } from '../../../../../lib/sms';

export async function GET(req:Request,{params}:{params:Promise<{id:string}>}){
  const key=authorizedKey(req); const data=await readData(); const apiKey=data.apiKeys.find((k:any)=>k.token===key&&k.enabled!==false);
  if(!apiKey||!hasScope(apiKey,'messages:send'))return NextResponse.json({error:'Unauthorized'},{status:401});
  const {id}=await params; const msg=data.messages.find((m:any)=>m.id===id);
  if(!msg)return NextResponse.json({error:'Message not found'},{status:404});
  return NextResponse.json({ok:true,data:{id:msg.id,status:msg.status,to:msg.to,sender_id:msg.senderName,provider_message_id:msg.providerMessageId||null,error:msg.error||null,created_at:msg.createdAt,updated_at:msg.updatedAt}});
}
