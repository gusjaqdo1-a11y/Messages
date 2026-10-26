import {NextResponse} from 'next/server';
import crypto from 'crypto';
import {firebaseDb} from '../../../../../lib/firebase-admin';
import {normalizeNumber} from '../../../../../lib/sms';

function uidOf(id:string){return 'app:'+crypto.createHash('sha256').update(id).digest('hex').slice(0,40)}
export async function POST(req:Request){try{
 const b=await req.json().catch(()=>({})); const installation=String(b.installation_id||'').trim();
 if(installation.length<32)return NextResponse.json({ok:false,error:'Invalid installation identity'},{status:400});
 const uid=uidOf(installation), db=firebaseDb(), ref=db.collection('profiles').doc(uid), snap=await ref.get();
 const now=Date.now(), phone=normalizeNumber(b.phone_number), token=String(b.fcm_token||'').trim(), name=String(b.display_name||'Messages SOM user').trim().slice(0,60);
 if(phone){const dup=await db.collection('profiles').where('phoneNumber','==',phone).limit(2).get(); if(dup.docs.some(d=>d.id!==uid))return NextResponse.json({ok:false,error:'This phone number is already linked to another Messages SOM account'},{status:409})}
 const current=snap.exists?snap.data()!:{}; const messagesId=String(current.messagesId||('MSOM-'+crypto.randomBytes(4).toString('hex').toUpperCase()));
 const data={uid,messagesId,displayName:name||current.displayName||'Messages SOM user',phoneNumber:phone||String(current.phoneNumber||''),photoUrl:String(current.photoUrl||''),fcmToken:token||String(current.fcmToken||''),enabled:current.enabled!==false,createdAt:Number(current.createdAt||now),updatedAt:now,allowReplies:current.allowReplies!==false,installationIdHash:crypto.createHash('sha256').update(installation).digest('hex')};
 await ref.set(data,{merge:true}); return NextResponse.json({ok:true,data:{...data,fcmToken:''}});
}catch(e:any){return NextResponse.json({ok:false,error:e?.message||'Registration failed'},{status:500})}}
