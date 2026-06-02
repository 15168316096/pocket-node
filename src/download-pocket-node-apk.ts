import { mkdir, rename, stat, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';

const defaultUrl = 'https://github.com/RaheemJnr/pocket-node/releases/download/v1.7.1/PocketNode-v1.7.1.apk';
const url = process.env.POCKET_NODE_APK_URL ?? defaultUrl;
const outputPath = resolve(process.env.APK_PATH ?? 'artifacts/PocketNode-v1.7.1.apk');

async function download(): Promise<void> {
  await mkdir(dirname(outputPath), { recursive: true });

  const response = await fetch(url);
  if (!response.ok || !response.body) {
    throw new Error(`Failed to download APK from ${url}: HTTP ${response.status} ${response.statusText}`);
  }

  const tempPath = `${outputPath}.tmp`;
  await writeFile(tempPath, new Uint8Array(await response.arrayBuffer()));
  await rename(tempPath, outputPath);

  const { size } = await stat(outputPath);
  if (size === 0) {
    throw new Error(`Downloaded APK is empty: ${outputPath}`);
  }

  console.log(`Downloaded Pocket Node APK to ${outputPath} (${size} bytes)`);
}

download().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
