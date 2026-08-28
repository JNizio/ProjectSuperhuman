(function(){
  'use strict';

  var STYLE_ID='psh-dark-mode-style';
  var ROOT_CLASS='psh-dark';
  var media=window.matchMedia?window.matchMedia('(prefers-color-scheme: dark)'):null;

  var css=`
  body.${ROOT_CLASS}{
    --navy:#8fc5ff;
    --blue:#58a9ff;
    --cyan:#52c4f2;
    --green:#4bc5ad;
    --violet:#a996ff;
    --orange:#f2a35f;
    --ink:#e9f1fb;
    --muted:#93a4b8;
    --bg:#09131f;
    --line:#26384b;
    --card:#111d2a;
    --soft:#172536;
    --danger:#ff8b94;
    background:linear-gradient(180deg,#09131f 0%,#0c1724 100%)!important;
    color:var(--ink)!important;
    color-scheme:dark;
  }
  body.${ROOT_CLASS} #app{background:transparent!important}
  body.${ROOT_CLASS} .screen{color:var(--ink)}

  /* Core surfaces used across Dashboard, Nutrition, Training, Sleep, Clinical, Body and Vitals. */
  body.${ROOT_CLASS} .icon-btn,
  body.${ROOT_CLASS} .home-note,
  body.${ROOT_CLASS} .callout,
  body.${ROOT_CLASS} .disclaimer,
  body.${ROOT_CLASS} .food-row,
  body.${ROOT_CLASS} .slider-wrap,
  body.${ROOT_CLASS} .secondary-btn,
  body.${ROOT_CLASS} .water-menu,
  body.${ROOT_CLASS} .detail-block,
  body.${ROOT_CLASS} .summary-card,
  body.${ROOT_CLASS} .history-card,
  body.${ROOT_CLASS} .pr-card,
  body.${ROOT_CLASS} .routine-row,
  body.${ROOT_CLASS} .exercise-card,
  body.${ROOT_CLASS} .workout-card,
  body.${ROOT_CLASS} .mind-card,
  body.${ROOT_CLASS} .protocol,
  body.${ROOT_CLASS} .sleep-food-card,
  body.${ROOT_CLASS} .sleep-link-card,
  body.${ROOT_CLASS} .insight-link-card,
  body.${ROOT_CLASS} .sleep967-card,
  body.${ROOT_CLASS} .sleep967-sync,
  body.${ROOT_CLASS} .sleep967-goal-card,
  body.${ROOT_CLASS} .hc-card,
  body.${ROOT_CLASS} .settings-card,
  body.${ROOT_CLASS} .settings-section,
  body.${ROOT_CLASS} .clinical-card,
  body.${ROOT_CLASS} .clinical-overview,
  body.${ROOT_CLASS} .clinical-condition,
  body.${ROOT_CLASS} .body-card,
  body.${ROOT_CLASS} .body-panel,
  body.${ROOT_CLASS} .body-history-card,
  body.${ROOT_CLASS} .vitals-card,
  body.${ROOT_CLASS} .metric-card,
  body.${ROOT_CLASS} .chart-card,
  body.${ROOT_CLASS} [class*="-card"]:not(.module-card):not(.category-card):not(.food-launch):not(.food-hero):not(.macro):not(.micro):not(.compound),
  body.${ROOT_CLASS} [class*="-panel"]{
    background:#111d2a!important;
    border-color:#26384b!important;
    box-shadow:none!important;
  }

  /* Preserve branded/hero gradients but make light gradient cards genuinely dark. */
  body.${ROOT_CLASS} .water-card{
    background:linear-gradient(145deg,#10293a 0%,#122232 58%,#0e283a 100%)!important;
    border-color:#23455c!important;
  }
  body.${ROOT_CLASS} .sleep967-hero,
  body.${ROOT_CLASS} [class*="hero"][style*="background"]{color:#fff}

  /* Text hierarchy. */
  body.${ROOT_CLASS} h1,
  body.${ROOT_CLASS} h2,
  body.${ROOT_CLASS} h3,
  body.${ROOT_CLASS} h4,
  body.${ROOT_CLASS} .row-title,
  body.${ROOT_CLASS} .food-name,
  body.${ROOT_CLASS} .section-title,
  body.${ROOT_CLASS} .brand-name,
  body.${ROOT_CLASS} .water-title,
  body.${ROOT_CLASS} .water-amount,
  body.${ROOT_CLASS} .food-k,
  body.${ROOT_CLASS} .sleep967-section-head b,
  body.${ROOT_CLASS} [class*="title"]{color:#e9f1fb!important}
  body.${ROOT_CLASS} .lead,
  body.${ROOT_CLASS} .section-sub,
  body.${ROOT_CLASS} .brand-sub,
  body.${ROOT_CLASS} .food-meta,
  body.${ROOT_CLASS} .food-row-mac,
  body.${ROOT_CLASS} .food-note,
  body.${ROOT_CLASS} .goal-caption,
  body.${ROOT_CLASS} .slider-scale,
  body.${ROOT_CLASS} .water-meta,
  body.${ROOT_CLASS} .muted,
  body.${ROOT_CLASS} [class*="-sub"],
  body.${ROOT_CLASS} [class*="-meta"],
  body.${ROOT_CLASS} [class*="caption"]{color:#93a4b8!important}

  /* Inputs, selectors, search fields and editable rows. */
  body.${ROOT_CLASS} input,
  body.${ROOT_CLASS} select,
  body.${ROOT_CLASS} textarea,
  body.${ROOT_CLASS} .search,
  body.${ROOT_CLASS} .food-search-row input,
  body.${ROOT_CLASS} .food-addbar input,
  body.${ROOT_CLASS} .food-addbar select,
  body.${ROOT_CLASS} .custom-grid input,
  body.${ROOT_CLASS} .custom-grid select,
  body.${ROOT_CLASS} .food-portion-field select,
  body.${ROOT_CLASS} .food-portion-field input{
    background:#0c1724!important;
    color:#e9f1fb!important;
    border-color:#30445a!important;
  }
  body.${ROOT_CLASS} input::placeholder,
  body.${ROOT_CLASS} textarea::placeholder{color:#75879a!important}

  /* Neutral chips, tabs and small controls. */
  body.${ROOT_CLASS} .food-chip,
  body.${ROOT_CLASS} .goal-presets button,
  body.${ROOT_CLASS} .food-tools button,
  body.${ROOT_CLASS} .food-addbar button,
  body.${ROOT_CLASS} .water-menu button,
  body.${ROOT_CLASS} .water-actions button,
  body.${ROOT_CLASS} [class*="chip"]:not(.active),
  body.${ROOT_CLASS} [class*="tab"]:not(.active),
  body.${ROOT_CLASS} [class*="pill"]:not(.active){
    background:#172536!important;
    color:#b7c9db!important;
    border-color:#2b4055!important;
  }

  /* Calendar/history/graph supporting surfaces. */
  body.${ROOT_CLASS} [class*="calendar"],
  body.${ROOT_CLASS} [class*="history"],
  body.${ROOT_CLASS} [class*="overview"],
  body.${ROOT_CLASS} [class*="chart"]{border-color:#26384b!important}
  body.${ROOT_CLASS} svg text{fill:#93a4b8!important}
  body.${ROOT_CLASS} svg [stroke="#dce5ef"],
  body.${ROOT_CLASS} svg [stroke="#e5ebf1"]{stroke:#30445a!important}

  /* Clinical/body pale blue tiles need contrast instead of remaining white. */
  body.${ROOT_CLASS} [style*="#fff"],
  body.${ROOT_CLASS} [style*="#ffffff"],
  body.${ROOT_CLASS} [style*="rgb(255, 255, 255)"]{border-color:#26384b}

  /* Keep intentionally white text on coloured hero/action surfaces. */
  body.${ROOT_CLASS} .module-card,
  body.${ROOT_CLASS} .category-card,
  body.${ROOT_CLASS} .food-launch,
  body.${ROOT_CLASS} .food-hero,
  body.${ROOT_CLASS} .macro,
  body.${ROOT_CLASS} .micro,
  body.${ROOT_CLASS} .compound,
  body.${ROOT_CLASS} .primary-btn,
  body.${ROOT_CLASS} .food-search-row button,
  body.${ROOT_CLASS} [class*="primary"]{color:#fff}

  body.${ROOT_CLASS} .food-launch button{background:#e9f1fb!important;color:#0b4169!important}
  body.${ROOT_CLASS} .food-delete{background:#321c23!important;color:#ff9fa8!important}
  body.${ROOT_CLASS} .water-menu-btn{background:#172536!important;border-color:#30445a!important;color:#8fc5ff!important}
  body.${ROOT_CLASS} .water-orb{background:#173246!important;border-color:#223b4f!important;box-shadow:inset 0 0 0 1px #31526a!important}
  body.${ROOT_CLASS} .water-percent{color:#d8efff!important}
  body.${ROOT_CLASS} .water-actions button{background:#172536!important;border-color:#30445a!important;color:#8fc5ff!important}
  body.${ROOT_CLASS} .goal-slider{background:#273b4e!important}
  body.${ROOT_CLASS} .goal-slider::-webkit-slider-thumb{border-color:#111d2a!important}
  body.${ROOT_CLASS} .water-adjust::-webkit-slider-thumb{background:#dcecff!important;border-color:#55b5ef!important}

  /* Avoid white flashes while routes are being replaced. */
  html:has(body.${ROOT_CLASS}){background:#09131f!important;color-scheme:dark}
  `;

  function nativeDark(){
    try{
      if(window.SuperhumanApp && typeof window.SuperhumanApp.isDarkMode==='function'){
        return !!window.SuperhumanApp.isDarkMode();
      }
    }catch(_e){}
    return media ? media.matches : false;
  }

  function install(){
    var style=document.getElementById(STYLE_ID);
    if(!style){
      style=document.createElement('style');
      style.id=STYLE_ID;
      style.textContent=css;
      document.head.appendChild(style);
    }
    document.body.classList.toggle(ROOT_CLASS,nativeDark());
  }

  install();
  if(media){
    var onChange=function(){install();};
    if(media.addEventListener)media.addEventListener('change',onChange);
    else if(media.addListener)media.addListener(onChange);
  }
  document.addEventListener('visibilitychange',function(){if(!document.hidden)install();});
})();
