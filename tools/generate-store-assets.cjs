// Native SVG layouts with real, unaltered app screenshots. No generated UI.
// Install sharp locally, or use the bundled Codex workspace dependency.
const fs = require('fs');
const path = require('path');
const os = require('os');
let sharp;
try { sharp = require('sharp'); } catch {
  sharp = require(path.join(os.homedir(), '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp'));
}
const root = path.resolve(__dirname, '..');
const store = path.join(root, 'store/google-play');
const source = path.join(store, 'source');
const graphics = path.join(store, 'graphics');
const esc = s => s.replaceAll('&', '&amp;').replaceAll('<', '&lt;');
const text = (s,x,y,size=36,color='#F4F3EF',weight=400) => `<text x="${x}" y="${y}" font-family="Segoe UI,Arial,sans-serif" font-size="${size}" font-weight="${weight}" fill="${color}">${esc(s)}</text>`;
const lines = (arr,x,y,size=36,color='#F4F3EF',weight=400,leading=1.25) => arr.map((s,i)=>text(s,x,y+i*size*leading,size,color,weight)).join('');
const rect = (x,y,w,h,color,r=0) => `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" fill="${color}"/>`;
const svg = (w,h,body) => `<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">${body}</svg>`;
const mark = `<path fill="#FFE15A" d="M31 37H40L44 31H62L66 37H77Q82 37 82 42V70Q82 75 77 75H31Q26 75 26 70V42Q26 37 31 37Z"/><circle cx="54" cy="55" r="14" fill="#151515"/><circle cx="54" cy="55" r="8" fill="#FFE15A"/><circle cx="72" cy="45" r="3" fill="#151515"/><path d="M72 26Q80 27 83 34M74 19Q87 21 91 32" fill="none" stroke="#FFE15A" stroke-width="3" stroke-linecap="round"/>`;
async function output(name,w,h,body,alpha=false) {
  const xml=svg(w,h,body); fs.writeFileSync(path.join(source,name+'.svg'),xml);
  let img=sharp(Buffer.from(xml)); img=alpha?img.ensureAlpha():img.flatten({background:'#151515'}).removeAlpha();
  await img.png().toFile(path.join(graphics,name+'.png'));
}
function base(n,category) {
  return rect(0,0,1080,1920,'#171819') + rect(0,0,1080,14,'#FFE15A') +
    `<g transform="translate(46 40) scale(1.1)">${mark}</g>`+text('RemoteCam',174,115,38,'#F4F3EF',600)+
    text(category,72,219,24,'#FFE15A',600)+rect(72,1852,936,1,'#454646')+text(`${n} / 06`,72,1895,23,'#B5B7B8')+text('TELEFON → KOMPUTER',713,1895,23,'#B5B7B8');
}
async function screenshotCard(n,name,category,title,file,caption) {
  const png=fs.readFileSync(path.join(store,'screenshots',file));
  const meta=await sharp(png).metadata(); const h=1260,w=h*meta.width/meta.height,x=(1080-w)/2;
  let body=base(n,category)+lines(title,72,318,66,'#F4F3EF',600,1.12)+rect(x-14,465,w+28,h+28,'#383A3B',18);
  body+=`<image x="${x}" y="479" width="${w}" height="${h}" xlink:href="data:image/png;base64,${png.toString('base64')}"/>`;
  body+=text(caption,72,1815,27,'#CFD0D0'); await output(name,1080,1920,body);
}
(async()=>{
  fs.mkdirSync(graphics,{recursive:true});fs.mkdirSync(source,{recursive:true});
  await output('play-icon-512',512,512,rect(0,0,512,512,'#151515')+`<g transform="scale(${512/108})">${mark}</g>`,true);
  await screenshotCard('01','01-camera','KAMERA W TWOJEJ SIECI',['Telefon staje się','kamerą do OBS.'],'01-camera-controls.png','Wybierz kamerę, rozdzielczość i format transmisji.');
  await screenshotCard('02','02-focus','KADR POD KONTROLĄ',['Ustaw kadr.','Zablokuj ostrość.'],'02-focus-options.png','Dostępność ręcznej ostrości i zoomu zależy od kamery.');
  await screenshotCard('03','03-receivers','WYBIERZ SWÓJ ODBIORNIK',['Obraz tam,','gdzie go potrzebujesz.'],'03-receiver-addresses.png','OBS Browser, go2rtc i odbiorniki MJPEG.');
  await screenshotCard('04','04-guide','POMOC W APLIKACJI',['Mniej zgadywania.','Więcej kontroli.'],'04-streaming-guide.png','Wskazówki dotyczące formatów, opóźnienia i sieci.');
  let b=base('05','OD TELEFONU DO PODGLĄDU')+lines(['Trzy kroki.','I masz obraz.'],72,318,76,'#F4F3EF',600,1.12);
  const steps=[['01','Połącz urządzenia',['Telefon i komputer w tej samej,','zaufanej sieci lokalnej.']],['02','Włącz Stream',['Wybierz format i ustaw kamerę.','Skopiuj adres z aplikacji.']],['03','Otwórz odbiornik',['Wklej adres w OBS Browser','lub skonfiguruj źródło w go2rtc.']]];
  steps.forEach(([num,title,desc],i)=>{const y=534+i*360;b+=rect(72,y,936,314,'#252729',12)+text(num,108,y+78,42,'#FFE15A',600)+text(title,108,y+153,45,'#F4F3EF',600)+lines(desc,108,y+219,34,'#BDC0C1');});
  b+=lines(['Transmisja wideo. Mikrofon komputera','dodajesz osobno w OBS.'],72,1717,31,'#CFD0D0');await output('05-how-it-works',1080,1920,b);
  b=base('06','FORMAT DOPASOWANY DO CIEBIE')+lines(['Twój odbiornik.','Twój wybór.'],72,318,76,'#F4F3EF',600,1.12);
  const formats=[['H.264 + WebRTC',['Sprzętowe kodowanie wideo.','Regulowany limit bitrate.','OBS Browser i wejście WHEP go2rtc.']],['JPEG Browser',['Obraz w źródle przeglądarkowym.','Sterowanie jakością JPEG.','Podgląd pod adresem /view.']],['MJPEG',['Strumień kolejnych klatek JPEG.','Zgodne odtwarzacze i narzędzia.','Odbiór pod adresem /cam.mjpeg.']]];
  formats.forEach(([title,desc],i)=>{const y=529+i*371;b+=rect(72,y,936,329,'#252729',12)+rect(72,y,5,329,'#FFE15A')+text(title,110,y+82,49,'#FFE15A',600)+lines(desc,110,y+151,33,'#CFD0D0',400,1.55);});
  b+=lines(['Płynność i opóźnienie zależą od telefonu,','sieci oraz ustawień odbiornika.'],72,1730,31,'#CFD0D0');await output('06-formats',1080,1920,b);
  await output('feature-graphic-1024x500',1024,500,rect(0,0,1024,500,'#FFE15A')+rect(601,0,423,500,'#292B2D')+
    text('RemoteCam',68,157,61,'#171819',700)+lines(['Kamera telefonu.','Obraz na komputerze.'],68,238,37,'#292B2D',600,1.35)+text('WebRTC · JPEG · MJPEG',68,388,24,'#292B2D')+
    `<g transform="translate(580 30) scale(4.0)">${mark}</g>`);
  // Plain screenshots, fitted without cropping or distorting the phone's tall aspect ratio.
  for(const [i,name] of ['01-camera-controls.png','03-receiver-addresses.png'].entries()) {
    await sharp(path.join(store,'screenshots',name)).resize(1080,1920,{fit:'contain',background:'#151515'}).flatten({background:'#151515'}).removeAlpha().png().toFile(path.join(graphics,`screenshot-${i+1}-1080x1920.png`));
  }
  const cards=fs.readdirSync(graphics).filter(n=>/^0[1-6]-.*\.png$/.test(n));
  const thumbs=await Promise.all(cards.map((n,i)=>sharp(path.join(graphics,n)).resize(270,480).toBuffer().then(input=>({input,left:(i%3)*270,top:Math.floor(i/3)*480}))));
  await sharp({create:{width:810,height:960,channels:3,background:'#151515'}}).composite(thumbs).png().toFile(path.join(store,'contact-sheet.png'));
  for(const n of fs.readdirSync(graphics).filter(n=>n.endsWith('.png'))){const m=await sharp(path.join(graphics,n)).metadata();console.log(n,m.width,m.height,m.channels,fs.statSync(path.join(graphics,n)).size);}
})().catch(e=>{console.error(e);process.exit(1)});
