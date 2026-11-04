const fs = require('fs');
const path = require('path');
const { withDangerousMod } = require('@expo/config-plugins');

/**
 * Give the Android launcher a simple, mask-safe vector icon. The adaptive icon
 * is written after Expo prebuild so launcher masks use the intended foreground
 * and solid background instead of baking a square bitmap into the foreground.
 */
function withGoBackIcon(config) {
  return withDangerousMod(config, ['android', async config => {
    const root = config.modRequest.platformProjectRoot;
    const res = path.join(root, 'app', 'src', 'main', 'res');
    const write = (relativePath, value) => {
      const file = path.join(res, relativePath);
      fs.mkdirSync(path.dirname(file), { recursive: true });
      fs.writeFileSync(file, value);
    };

    write('drawable/go_back_icon_foreground.xml', `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="1024"
    android:viewportHeight="1024">
    <path
        android:fillColor="@android:color/transparent"
        android:pathData="M650,735 C775,665 775,525 715,448 C660,378 595,348 520,348 L392,348"
        android:strokeColor="#C7F485"
        android:strokeWidth="92"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
    <path
        android:fillColor="#C7F485"
        android:pathData="M250,348 L410,235 L410,461 Z" />
</vector>
`);

    write('values/go_back_icon_colors.xml', `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="go_back_icon_background">#081B14</color>
</resources>
`);

    const adaptiveIcon = `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/go_back_icon_background" />
    <foreground android:drawable="@drawable/go_back_icon_foreground" />
</adaptive-icon>
`;
    write('mipmap-anydpi-v26/ic_launcher.xml', adaptiveIcon);
    write('mipmap-anydpi-v26/ic_launcher_round.xml', adaptiveIcon);
    return config;
  }]);
}

module.exports = withGoBackIcon;
