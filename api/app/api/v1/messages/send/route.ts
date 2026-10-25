import { NextResponse } from 'next/server';
import { authorizedKey, hasScope, sendSms } from '../../../../lib/sms';
import { readData } from '../../../../lib/sms';

export async function POST(req:Request){
  try{
    const key=authorizedKey(req); const data=await readData();
    const apiKey=data.apiKeys.find((k:any)=>k.token===key&&k.enabled!==false);
    if(!apiKey||!hasScope(apiKey,'messages:send'))return NextResponse.json({error:'Unauthorized'},{status:401});
    const body=await req.json().catch(()=>({}));
    if(!body.sender_id||!body.to||!body.text)return NextResponse.json({error:'sender_id, to and text are required'},{status:400});
    const msg=await sendSms({senderId:String(body.sender_id),to:String(body.to),text:String(body.text),userId:apiKey.userId||'api',source:'public-api'});
    return NextResponse.json({ok:msg.status==='sent',data:{id:msg.id,status:msg.status,provider_message_id:msg.providerMessageId||null,error:msg.error||null}}, {status:msg.status==='failed'?502:200});
  }catch(e:any){return NextResponse.json({ok:false,error:e?.message||'Send failed'},{status:400});}
}
