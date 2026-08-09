(function(){
  'use strict';
  function status(text){const el=document.getElementById('bpScanStatus');if(el)el.textContent=text}
  function setField(id,v){const el=document.getElementById(id);if(el&&v!==null&&v!==undefined&&v!=='')el.value=String(v)}
  window.ProjectSuperhumanBP={
    onNativeStatus:function(raw){try{const j=JSON.parse(raw);status(j.message||'Working…')}catch(_){status(String(raw||'Working…'))}},
    onNativeError:function(msg){status(msg||'Could not read the monitor display.')},
    onNativeResult:function(raw){
      try{
        const j=JSON.parse(raw||'{}');
        setField('bpSys',j.systolic);setField('bpDia',j.diastolic);setField('bpPulse',j.pulse);
        if(j.ok){status('Detected '+j.systolic+'/'+j.diastolic+' mmHg'+(j.pulse?' · pulse '+j.pulse+' bpm':'')+'. Verify before saving.');}
        else status('I captured the display but could not confidently identify SYS and DIA. Try again closer/straighter or enter manually.');
      }catch(_){status('Camera capture finished, but the reading could not be parsed.')}
    }
  };
  document.addEventListener('click',function(e){
    const b=e.target&&e.target.closest?e.target.closest('button[data-action="bp-camera"]'):null;
    if(!b)return;
    if(window.NativeBP&&typeof NativeBP.capture==='function'){
      e.preventDefault();e.stopPropagation();if(e.stopImmediatePropagation)e.stopImmediatePropagation();
      status('Opening native camera…');NativeBP.capture();
    }
  },true);
})();
