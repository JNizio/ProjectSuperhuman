(function(){
  if(document.getElementById('psh-safe-area-fix')) return;
  var style=document.createElement('style');
  style.id='psh-safe-area-fix';
  style.textContent=`
    html,body{min-height:100%;}
    #app{
      min-height:100dvh;
      padding-bottom:max(96px, calc(42px + env(safe-area-inset-bottom, 0px))) !important;
    }
    .screen{
      padding-bottom:max(54px, env(safe-area-inset-bottom, 0px));
    }
  `;
  document.head.appendChild(style);
})();
