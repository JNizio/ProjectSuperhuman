(function(){
  'use strict';
  const SYSTEMS=[
    ['brain','Brain'],['thyroid','Thyroid'],['heart','Heart'],['liver','Liver'],['kidneys','Kidneys'],['bone','Bone'],
    ['blood','Blood'],['immune','Immune'],['hormones','Hormones'],['vitamins','Vitamins & minerals'],['metabolic','Metabolic']
  ];
  const BODY_IDS=['brain','thyroid','heart','liver','kidneys','bone'];
  const LABEL=Object.fromEntries(SYSTEMS);
  let busy=false;

  function installStyles(){
    if(document.getElementById('pshClinicalSegmentStyles'))return;
    const s=document.createElement('style');
    s.id='pshClinicalSegmentStyles';
    s.textContent=`
      .clinical-system-map.psh-segmented{padding:17px 14px 15px;background:linear-gradient(155deg,#f5faff 0%,#fff 72%)}
      .psh-body-legend{display:flex;gap:11px;flex-wrap:wrap;margin:7px 0 12px;font-size:8.5px;font-weight:850;color:#687f91}
      .psh-body-legend span{display:inline-flex;align-items:center;gap:5px}.psh-body-legend i{width:8px;height:8px;border-radius:50%;display:inline-block}
      .psh-body-legend .no-data{background:#0D6CB4}.psh-body-legend .healthy{background:#2f9d68}.psh-body-legend .abnormal{background:#cf574f}
      .psh-body-stage{display:grid;grid-template-columns:minmax(0,1fr) 118px;gap:13px;align-items:center;margin:2px 0 12px;padding:10px 8px 8px;border-radius:20px;background:linear-gradient(145deg,#eef6fc,#fbfdff);border:1px solid #e0ebf3}
      .psh-body-svg{width:100%;max-width:205px;height:286px;display:block;margin:0 auto;overflow:visible;filter:drop-shadow(0 7px 12px rgba(15,57,90,.08))}
      .psh-body-outline{fill:#f4f8fb;stroke:#c7d9e6;stroke-width:2.2}
      .psh-clinical-segment{cursor:pointer;outline:none;transition:filter .16s ease,opacity .16s ease}.psh-clinical-segment .seg-fill{stroke:#fff;stroke-width:3;stroke-linejoin:round;transition:fill .18s ease,stroke .18s ease}.psh-clinical-segment.state-empty .seg-fill{fill:#0D6CB4}.psh-clinical-segment.state-normal .seg-fill{fill:#2f9d68}.psh-clinical-segment.state-abnormal .seg-fill{fill:#cf574f}.psh-clinical-segment:hover{filter:brightness(1.04)}.psh-clinical-segment.active .seg-fill{stroke:#082d66;stroke-width:5;filter:drop-shadow(0 0 5px rgba(8,45,102,.3))}
      .psh-body-divider{stroke:rgba(255,255,255,.78);stroke-width:2;pointer-events:none}
      .psh-body-side{display:grid;gap:7px}.psh-body-side button{width:100%;min-height:37px;padding:7px 8px;border-radius:12px;text-align:left;background:#fff;border:1px solid #dbe7f0;color:#31536b;font-size:8.5px;font-weight:900;line-height:1.2;box-shadow:0 3px 9px rgba(18,58,88,.035)}
      .psh-body-side button:before,.clinical-system-chip.psh-system-chip:before{content:'';display:inline-block;width:7px;height:7px;border-radius:50%;margin-right:6px;vertical-align:0;background:#0D6CB4}.psh-body-side button.normal:before,.clinical-system-chip.psh-system-chip.normal:before{background:#2f9d68}.psh-body-side button.abnormal:before,.clinical-system-chip.psh-system-chip.abnormal:before{background:#cf574f}.psh-body-side button.active{background:#173f5d;color:#fff;border-color:#173f5d}
      .psh-system-title{font-size:8px;letter-spacing:.09em;text-transform:uppercase;color:#8798a6;font-weight:900;margin:1px 1px 5px}.clinical-system-chips.psh-systemic{margin-top:3px;padding-bottom:5px}.clinical-system-chip.psh-system-chip{display:inline-flex;align-items:center}.clinical-system-chip.psh-system-chip:before{margin-right:5px}
      .psh-body-summary{margin-top:10px;padding:11px 12px;border-radius:14px;background:#f3f7fa;border:1px solid #e5edf3;color:#61798c;font-size:9px;line-height:1.4}.psh-body-summary b{color:#23465f}.psh-body-summary .bad{color:#a44741}.psh-body-summary .good{color:#28764f}
      @media(max-width:390px){.psh-body-stage{grid-template-columns:minmax(0,1fr) 105px;gap:8px;padding-left:3px;padding-right:7px}.psh-body-svg{max-width:184px;height:270px}.psh-body-side button{font-size:8px;padding:6px}.psh-body-legend{gap:8px}}
    `;
    document.head.appendChild(s);
  }

  function stateFrom(el){
    if(!el)return'empty';
    if(el.classList.contains('abnormal'))return'abnormal';
    if(el.classList.contains('normal'))return'normal';
    return'empty';
  }

  function collectStates(map){
    const out={};
    SYSTEMS.forEach(([id])=>{
      const el=map.querySelector(`[data-system="${id}"]`);
      out[id]=stateFrom(el);
    });
    return out;
  }

  function segment(id,shape,states){
    const st=states[id]||'empty';
    return `<g class="psh-clinical-segment state-${st}" data-system="${id}" role="button" tabindex="0" aria-label="${LABEL[id]}: ${st==='normal'?'healthy data':st==='abnormal'?'abnormal result':'no data'}"><title>${LABEL[id]}</title>${shape}</g>`;
  }

  function makeSvg(states){
    return `<svg class="psh-body-svg" viewBox="0 0 180 320" aria-label="Interactive clinical body map">
      <path class="psh-body-outline" d="M71 70 Q90 61 109 70 L126 91 L143 157 Q145 166 136 169 Q128 171 124 162 L113 119 L116 179 L126 294 Q127 307 116 309 Q106 310 103 298 L90 211 L77 298 Q74 310 64 309 Q53 307 54 294 L64 179 L67 119 L56 162 Q52 171 44 169 Q35 166 37 157 L54 91 Z"/>
      ${segment('brain','<circle class="seg-fill" cx="90" cy="39" r="25"/>',states)}
      ${segment('thyroid','<path class="seg-fill" d="M78 66 Q90 61 102 66 L101 80 Q90 85 79 80 Z"/>',states)}
      ${segment('heart','<path class="seg-fill" d="M67 82 Q90 75 113 82 L117 120 Q90 132 63 120 Z"/>',states)}
      ${segment('liver','<path class="seg-fill" d="M64 121 Q91 129 116 121 L115 153 Q91 162 63 153 Z"/>',states)}
      ${segment('kidneys','<path class="seg-fill" d="M63 154 Q90 162 115 154 L114 184 Q91 194 64 184 Z"/>',states)}
      ${segment('bone','<path class="seg-fill" d="M54 91 L39 157 Q37 166 45 169 Q53 171 57 162 L68 120 Z M112 120 L123 162 Q127 171 135 169 Q143 166 141 157 L126 91 Z M65 183 L55 294 Q54 306 65 308 Q75 309 78 297 L90 211 L102 297 Q105 309 115 308 Q126 306 125 294 L114 183 Z"/>',states)}
      <path class="psh-body-divider" d="M64 121 Q90 130 116 121 M63 154 Q90 162 115 154 M64 184 Q90 194 114 184"/>
    </svg>`;
  }

  function chip(id,states,active){
    const st=states[id]||'empty';
    return `<button class="clinical-system-chip psh-system-chip ${st} ${active?'active':''}" data-action="clinical-system-filter" data-system="${id}">${LABEL[id]}</button>`;
  }

  function updateSummary(map,id){
    const box=map.querySelector('.psh-body-summary');
    if(!box)return;
    if(!id||id==='all'){
      box.innerHTML='<b>Whole clinical map.</b> Tap any coloured region or system below to filter your latest markers.';
      return;
    }
    setTimeout(()=>{
      const list=document.getElementById('clinicalLatestList');
      if(!list)return;
      const dots=[...list.querySelectorAll('.clinical-marker-status-dot')];
      const normal=dots.filter(x=>x.classList.contains('normal')).length;
      const abnormal=dots.filter(x=>x.classList.contains('high')||x.classList.contains('low')).length;
      const unknown=Math.max(0,dots.length-normal-abnormal);
      const bits=[];
      if(normal)bits.push(`<span class="good">${normal} healthy</span>`);
      if(abnormal)bits.push(`<span class="bad">${abnormal} abnormal</span>`);
      if(unknown)bits.push(`${unknown} range unknown`);
      box.innerHTML=`<b>${LABEL[id]||id}</b> · ${dots.length?bits.join(' · '):'No saved markers in this system yet.'}`;
    },0);
  }

  function syncActive(map,id){
    map.querySelectorAll('.psh-clinical-segment').forEach(x=>x.classList.toggle('active',x.dataset.system===id));
    updateSummary(map,id);
  }

  function upgrade(map){
    if(!map||map.dataset.pshSegmented==='1')return;
    const states=collectStates(map);
    map.dataset.pshSegmented='1';
    map.classList.add('psh-segmented');
    map.innerHTML=`
      <div class="clinical-system-head"><div><b>Clinical body map</b><div class="clinical-meta">Latest marker status by body system</div></div><span>Tap a coloured region to filter markers</span></div>
      <div class="psh-body-legend"><span><i class="no-data"></i>Blue · no data</span><span><i class="healthy"></i>Green · healthy</span><span><i class="abnormal"></i>Red · abnormal</span></div>
      <div class="psh-body-stage">
        <div>${makeSvg(states)}</div>
        <div class="psh-body-side">
          ${BODY_IDS.map(id=>`<button class="${states[id]||'empty'}" data-action="clinical-system-filter" data-system="${id}">${LABEL[id]}</button>`).join('')}
        </div>
      </div>
      <div class="psh-system-title">Whole-body systems</div>
      <div class="clinical-system-chips psh-systemic">
        <button class="clinical-system-chip psh-system-chip active" data-action="clinical-system-filter" data-system="all">All systems</button>
        ${SYSTEMS.filter(([id])=>!BODY_IDS.includes(id)).map(([id])=>chip(id,states,false)).join('')}
      </div>
      <div class="psh-body-summary"><b>Whole clinical map.</b> Tap any coloured region or system below to filter your latest markers.</div>
      <div class="clinical-system-note">A region turns red if any linked latest marker is outside its saved reference range. One marker may contribute to more than one system.</div>`;

    map.addEventListener('click',e=>{
      const seg=e.target.closest('.psh-clinical-segment');
      if(!seg)return;
      e.preventDefault();
      const id=seg.dataset.system;
      const proxy=map.querySelector(`button[data-system="${id}"]`);
      if(proxy)proxy.click();
    });
    map.addEventListener('keydown',e=>{
      const seg=e.target.closest('.psh-clinical-segment');
      if(seg&&(e.key==='Enter'||e.key===' ')){e.preventDefault();const proxy=map.querySelector(`button[data-system="${seg.dataset.system}"]`);proxy&&proxy.click()}
    });
    map.addEventListener('click',e=>{
      const b=e.target.closest('button[data-action="clinical-system-filter"]');
      if(!b)return;
      setTimeout(()=>syncActive(map,b.dataset.system||'all'),0);
    });
  }

  function scan(){
    if(busy)return;busy=true;
    try{document.querySelectorAll('.clinical-system-map').forEach(upgrade)}finally{busy=false}
  }

  installStyles();
  const observer=new MutationObserver(()=>scan());
  observer.observe(document.documentElement,{subtree:true,childList:true});
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',scan);else scan();
  setTimeout(scan,250);
})();
