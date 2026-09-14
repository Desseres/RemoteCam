// Rasterize our repository SVGs for Windows PE icons and Inno Setup resources.
// Run with Node.js and sharp available on NODE_PATH. No external images/fonts.
const fs = require('fs');
const path = require('path');
const sharp = require('sharp');
const assets = path.join(__dirname, 'assets');
(async () => {
  const svg = path.join(assets, 'brandmark.svg');
  await sharp(svg).resize(256,256).png().toFile(path.join(assets, 'brandmark.png'));
  await sharp(path.join(assets, 'installer-panel.svg')).png().toFile(path.join(assets, 'installer-panel.png'));
  const sizes = [16,20,24,32,40,48,64,128,256];
  const images = await Promise.all(sizes.map(size => sharp(svg).resize(size,size).png().toBuffer()));
  const header = Buffer.alloc(6 + 16*sizes.length);
  header.writeUInt16LE(1,2); header.writeUInt16LE(sizes.length,4);
  let offset = header.length;
  sizes.forEach((size,i) => {
    const entry = 6+16*i;
    header[entry] = header[entry+1] = size === 256 ? 0 : size;
    header.writeUInt16LE(1,entry+4); header.writeUInt16LE(32,entry+6);
    header.writeUInt32LE(images[i].length,entry+8); header.writeUInt32LE(offset,entry+12);
    offset += images[i].length;
  });
  fs.writeFileSync(path.join(assets, 'RemoteCam.ico'), Buffer.concat([header,...images]));
  console.log('Generated logo, installer panel and nine-size Windows ICO.');
})().catch(e => { console.error(e); process.exit(1); });
