import {NextResponse} from 'next/server';
import crypto from 'crypto';
import {firebaseDb} from '../../../../../lib/firebase-admin';

function uidOf(id:string){return 'app:'+crypto.createHash('sha256').update(id).digest('hex').slice(0,40)}

export async function POST(req:Request){
  try{
    const b=await req.json().catch(()=>({}));
    const installation=String(b.installation_id||'').trim();
    const other=String(b.other_uid||'').trim();
    if(installation.length<32||!other)return NextResponse.json({ok:false,error:'Invalid conversation request'},{status:400});
    const me=uidOf(installation),db=firebaseDb();
    const [meSnap,otherSnap]=await Promise.all([
      db.collection('profiles').doc(me).get(),
      db.collection('profiles').doc(other).get()
    ]);
    if(!meSnap.exists)return NextResponse.json({ok:false,error:'Messages SOM account is not registered'},{status:401});
    if(!otherSnap.exists||otherSnap.data()?.enabled===false)return NextResponse.json({ok:false,error:'Messages SOM user not found'},{status:404});
    const id=[me,other].sort().join('_');
    const snap=await db.collection('appConversations').doc(id).collection('messages').orderBy('createdAt','asc').limitToLast(500).get();
    const data=snap.docs.map(d=>d.data()).filter((x:any)=>x.deleted!==true).map((x:any)=>({...x,mine:x.senderUid===me}));
    return NextResponse.json({ok:true,data});
  }catch(e:any){return NextResponse.json({ok:false,error:e?.message||'Conversation failed'},{status:500})}
}
