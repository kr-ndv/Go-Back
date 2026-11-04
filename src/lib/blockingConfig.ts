export type BlockingConfig = {
  blockYoutube: boolean;
  blockShorts: boolean;
  blockReels: boolean;
  blockSpotlight: boolean;
  blockAdultSites: boolean;
  blockSplitScreen: boolean;
  strictUninstallProtection: boolean;
  strictMode: boolean;
  blockOnlyDuringFocus: boolean;
  allowedChannels: string[];
  blockedPackages: string[];
};

export const DEFAULT_BLOCKING_CONFIG: BlockingConfig = {
  blockYoutube: false,
  blockShorts: false,
  blockReels: false,
  blockSpotlight: false,
  blockAdultSites: false,
  blockSplitScreen: false,
  strictUninstallProtection: false,
  strictMode: false,
  blockOnlyDuringFocus: false,
  allowedChannels: [],
  blockedPackages: [],
};

export function normalizeBlockingConfig(value: Partial<BlockingConfig> | null | undefined): BlockingConfig {
  const source = value ?? {};
  return {
    blockYoutube: source.blockYoutube === true,
    blockShorts: source.blockShorts === true,
    blockReels: source.blockReels === true,
    blockSpotlight: source.blockSpotlight === true,
    blockAdultSites: source.blockAdultSites === true,
    blockSplitScreen: source.blockSplitScreen === true,
    strictUninstallProtection: source.strictUninstallProtection === true,
    strictMode: source.strictMode === true,
    blockOnlyDuringFocus: source.blockOnlyDuringFocus === true,
    allowedChannels: Array.isArray(source.allowedChannels)
      ? [...new Set(source.allowedChannels.filter(item => typeof item === 'string').map(item => item.trim().replace(/\s+/g, ' ')).filter(Boolean))].slice(0, 50)
      : [],
    blockedPackages: Array.isArray(source.blockedPackages)
      ? [...new Set(source.blockedPackages.filter(item => typeof item === 'string' && /^[A-Za-z0-9_.]+$/.test(item) && item.includes('.') && item !== 'com.goback.focus'))].slice(0, 100)
      : [],
  };
}
