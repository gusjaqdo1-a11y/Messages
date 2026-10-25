import { NextResponse } from 'next/server';
import { authorizedKey, hasScope, readData } from '../../../../lib/sms';

export async function GET(req:Request){
  const key=authorizedKey(req); const data=await readData();
  const apiKey=data.apiKeys.find((k:any)=>k.token===key&&k.enabled!==false);
  if(!apiKey||!hasScope(apiKey,'messages:send'))return NextResponse.json({error:'Unauthorized'},{status:401});
  const list=data.senderIds.filter((s:any)=>s.enabled!==false).map((s:any)=>({id:s.id,name:s.name,providerSenderId:s.providerSenderId,provider:s.provider,countries:s.countries||[],allowReplies:Boolean(s.allowReplies),dailyLimit:s.dailyLimit||0,monthlyLimit:s.monthlyLimit||0}));
  return NextResponse.json({ok:true,data:list});
}
