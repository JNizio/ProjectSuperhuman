(function(){
  if(document.getElementById('psh-safe-area-fix')) return;
  var style=document.createElement('style');
  style.id='psh-safe-area-fix';
  style.textContent=`
    html,body{min-height:100%;}
    #app{
      min-height:100dvh;
      padding-bottom:180px !important;
    }
    .screen{
      padding-bottom:120px !important;
    }
    .screen::after{
      content:"";
      display:block;
      height:96px;
      width:100%;
      pointer-events:none;
    }
  `;
  document.head.appendChild(style);
})();
