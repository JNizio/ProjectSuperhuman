(function(){
'use strict';
const VERSION='10.1.0-science.1';
const normalise=s=>String(s||'').trim().toLowerCase().replace(/[^a-z0-9]+/g,' ');
function classify(m){
  const value=Number(m?.value),low=Number(m?.low),high=Number(m?.high),hasLow=m?.low!==null&&m?.low!==undefined&&Number.isFinite(low),hasHigh=m?.high!==null&&m?.high!==undefined&&Number.isFinite(high);
  if(!Number.isFinite(value))return{status:'unknown',reason:'No numeric result.',delta:null};
  if(!hasLow||!hasHigh)return{status:'unknown',reason:'No complete saved reference range.',delta:null};
  if(value<low)return{status:'low',reason:`Below supplied reference range (${low}–${high}${m.unit?' '+m.unit:''}).`,delta:value-low};
  if(value>high)return{status:'high',reason:`Above supplied reference range (${low}–${high}${m.unit?' '+m.unit:''}).`,delta:value-high};
  return{status:'normal',reason:'Within supplied reference range.',delta:Math.min(value-low,high-value)};
}
function aggregateSystem(name,markers){
  const interpreted=(markers||[]).map(m=>({...m,...classify(m)}));
  const normal=interpreted.filter(x=>x.status==='normal').length,abnormal=interpreted.filter(x=>x.status==='high'||x.status==='low').length,unknown=interpreted.length-normal-abnormal;
  return{system:name,status:abnormal?'abnormal':normal&&unknown===0?'normal':'unknown',normalCount:normal,abnormalCount:abnormal,unknownCount:unknown,markers:interpreted,engineVersion:VERSION};
}
function trend(values){
  const v=(values||[]).map(Number).filter(Number.isFinite);if(v.length<2)return{direction:'insufficient',points:v.length,change:null,percentChange:null};
  const first=v[0],last=v[v.length-1],change=last-first,tol=Math.max(.000001,Math.abs(first)*.01),direction=Math.abs(change)<=tol?'stable':change>0?'rising':'falling';
  return{direction,points:v.length,change,percentChange:Math.abs(first)<1e-9?null:change/first*100};
}
function pearson(a,b){a=(a||[]).map(Number);b=(b||[]).map(Number);if(a.length!==b.length||a.length<3||a.some(x=>!Number.isFinite(x))||b.some(x=>!Number.isFinite(x)))return null;const ma=a.reduce((s,x)=>s+x,0)/a.length,mb=b.reduce((s,x)=>s+x,0)/b.length;let num=0,da=0,db=0;for(let i=0;i<a.length;i++){const x=a[i]-ma,y=b[i]-mb;num+=x*y;da+=x*x;db+=y*y}const den=Math.sqrt(da*db);return den?num/den:null}
function bloodPressure(sys,dia,pulse){sys=Number(sys);dia=Number(dia);pulse=pulse===null||pulse===undefined||pulse===''?null:Number(pulse);const plausible=Number.isFinite(sys)&&Number.isFinite(dia)&&sys>=70&&sys<=280&&dia>=35&&dia<=180&&sys>dia&&(pulse===null||(Number.isFinite(pulse)&&pulse>=30&&pulse<=220));const pp=sys-dia;return{systolic:sys,diastolic:dia,pulse,pulsePressure:Number.isFinite(pp)?pp:null,meanArterialPressure:Number.isFinite(pp)?dia+pp/3:null,plausible,engineVersion:VERSION}}
const LINKS={
  liver:['bilirubin','alt','alanine aminotransferase','ast','aspartate aminotransferase','alp','alkaline phosphatase','ggt','albumin','total protein'],
  kidneys:['creatinine','egfr','urea','sodium','potassium'],
  blood:['haemoglobin','hemoglobin','rbc','red blood cell','wbc','white blood cell','platelet','haematocrit','hematocrit','mcv','mch','mchc'],
  iron:['ferritin','serum iron','transferrin','transferrin saturation','tibc'],
  thyroid:['tsh','thyroid stimulating hormone','free t4','ft4','free t3','ft3'],
  bone:['calcium','phosphate','alkaline phosphatase','vitamin d'],
  metabolic:['hba1c','glucose','cholesterol','ldl','hdl','triglycerides']
};
function belongs(name,aliases){const n=normalise(name);return aliases.some(a=>n===normalise(a)||n.includes(normalise(a)))}
function analyseClinical(markers){
  markers=Array.isArray(markers)?markers:[];const interpreted=markers.map(m=>({...m,...classify(m)}));const systems={};Object.entries(LINKS).forEach(([system,aliases])=>{const ms=markers.filter(m=>belongs(m.name||m.marker||'',aliases));systems[system]=aggregateSystem(system,ms)});
  const abnormal=interpreted.filter(x=>x.status==='high'||x.status==='low');const insights=[];
  const liver=systems.liver?.markers||[],bili=liver.find(x=>normalise(x.name||x.marker).includes('bilirubin'));
  if(bili&&(bili.status==='high'||bili.status==='low')){const others=liver.filter(x=>x!==bili);if(others.length&&others.every(x=>x.status==='normal'||x.status==='unknown'))insights.push({id:'clinical.isolated_bilirubin',type:'pattern',status:'attention',confidence:.9,title:'Isolated bilirubin pattern',explanation:'Bilirubin is outside its supplied range while the other available linked liver markers are not outside range.',evidence:liver.map(x=>({name:x.name||x.marker,value:x.value,status:x.status}))})}
  return{engineVersion:VERSION,markerCount:interpreted.length,abnormalCount:abnormal.length,markers:interpreted,systems,insights};
}
function pairedAssociation(xs,ys,label){const r=pearson(xs,ys),n=Math.min(xs?.length||0,ys?.length||0);return{label,n,r,strength:r===null||n<5?'insufficient':Math.abs(r)<.25?'no-clear':Math.abs(r)<.5?'early':Math.abs(r)<.7?'moderate':'strong',observational:true,engineVersion:VERSION}}
window.ProjectSuperhumanScience=Object.freeze({version:VERSION,classify,aggregateSystem,trend,pearson,bloodPressure,analyseClinical,pairedAssociation});
window.dispatchEvent(new CustomEvent('project-superhuman-science-ready',{detail:{version:VERSION}}));
})();
