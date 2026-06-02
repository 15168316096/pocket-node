import { _android as android, AndroidDevice } from 'playwright';
import { mkdir } from 'node:fs/promises';

type Env = {
  apkPath?: string;
  packageName?: string;
  serial?: string;
  screenshotDir: string;
};

const env: Env = {
  apkPath: process.env.APK_PATH,
  packageName: process.env.POCKET_NODE_PACKAGE,
  serial: process.env.DEVICE_SERIAL,
  screenshotDir: process.env.SCREENSHOT_DIR ?? 'screenshots',
};

async function selectDevice(): Promise<AndroidDevice> {
  const devices = await android.devices();
  if (devices.length === 0) {
    throw new Error('No Android device found. Start an emulator or connect a device, then verify `adb devices`.');
  }

  const device = env.serial ? devices.find((candidate) => candidate.serial() === env.serial) : devices[0];
  if (!device) {
    throw new Error(`Android device ${env.serial} was not found. Connected devices: ${devices.map((d) => d.serial()).join(', ')}`);
  }

  return device;
}

function parsePackages(output: string): Set<string> {
  return new Set(
    output
      .split('\n')
      .map((line) => line.trim().replace(/^package:/, ''))
      .filter(Boolean),
  );
}

async function listThirdPartyPackages(device: AndroidDevice): Promise<Set<string>> {
  return parsePackages((await device.shell('pm list packages -3')).toString());
}

async function inferInstalledPackage(device: AndroidDevice, beforeInstall: Set<string>): Promise<string> {
  const afterInstall = await listThirdPartyPackages(device);
  const installed = [...afterInstall].filter((packageName) => !beforeInstall.has(packageName));

  if (installed.length === 1) {
    return installed[0];
  }

  const candidates = installed.filter((packageName) => /pocket|wallet|ckb/i.test(packageName));
  if (candidates.length === 1) {
    return candidates[0];
  }

  throw new Error(
    `Could not infer installed package. Set POCKET_NODE_PACKAGE explicitly. New packages: ${installed.join(', ') || '(none)'}`,
  );
}

async function launchPocketNode(device: AndroidDevice, packageName: string): Promise<void> {
  await device.shell(`am force-stop ${packageName}`);
  await device.shell(`monkey -p ${packageName} -c android.intent.category.LAUNCHER 1`);
}

async function takeScreenshot(device: AndroidDevice, name: string): Promise<void> {
  await mkdir(env.screenshotDir, { recursive: true });
  await device.screenshot({ path: `${env.screenshotDir}/${name}.png` });
}

async function maybeTapByText(device: AndroidDevice, text: string): Promise<boolean> {
  const selector = { text };
  try {
    await device.tap(selector, { timeout: 2000 });
    return true;
  } catch {
    return false;
  }
}

async function run(): Promise<void> {
  const device = await selectDevice();
  try {
    console.log(`Using Android device: ${device.model()} (${device.serial()})`);

    const beforeInstall = env.apkPath && !env.packageName ? await listThirdPartyPackages(device) : new Set<string>();
    let packageName = env.packageName;

    if (env.apkPath) {
      console.log(`Installing APK: ${env.apkPath}`);
      await device.installApk(env.apkPath);
      packageName ??= await inferInstalledPackage(device, beforeInstall);
    }

    if (!packageName) {
      throw new Error('POCKET_NODE_PACKAGE is required when APK_PATH is not set.');
    }

    console.log(`Launching package: ${packageName}`);
    await launchPocketNode(device, packageName);
    await device.wait({ text: /Pocket Node|Create|Import|Wallet|Start|Get Started/ }, { timeout: 30000 }).catch(() => undefined);
    await takeScreenshot(device, '01-launch');

    const accepted = await maybeTapByText(device, 'Get Started')
      || await maybeTapByText(device, 'Start')
      || await maybeTapByText(device, 'Create Wallet');

    if (accepted) {
      await takeScreenshot(device, '02-after-primary-action');
    }

    console.log('Smoke run complete. Inspect screenshots for current UI state.');
  } finally {
    await device.close();
  }
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

