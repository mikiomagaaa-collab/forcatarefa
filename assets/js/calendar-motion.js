export function animateMonth(container,direction=1){
 if(matchMedia('(prefers-reduced-motion: reduce)').matches)return;
 container.getAnimations().forEach(animation=>animation.cancel());
 container.animate([{opacity:.3,transform:`translateX(${direction*16}px)`},{opacity:1,transform:'translateX(0)'}],{duration:260,easing:'cubic-bezier(.2,.8,.2,1)'});
}
