const fs = require('fs');
const path = require('path');
const { withDangerousMod } = require('@expo/config-plugins');

function replaceRequired(source, search, replacement, label) {
  if (!source.includes(search)) throw new Error(`Go Back native reliability: expected ${label} source was not found.`);
  return source.split(search).join(replacement);
}

function fixKotlinRegexes(source) {
  return source
    .split(String.raw`Regex("\s+")`).join(String.raw`Regex("""\s+""")`)
    .split(String.raw`Regex("[^\p{L}\p{N}@]+")`).join(String.raw`Regex("""[^\p{L}\p{N}@]+""")`)
    .split(String.raw`Regex("[^\p{L}\p{N}]+")`).join(String.raw`Regex("""[^\p{L}\p{N}]+""")`)
    .split(String.raw`Regex("\d+(\.\d+){0,3}")`).join(String.raw`Regex("""\d+(\.\d+){0,3}""")`);
}

function patchBlockingService(source) {
  let next = fixKotlinRegexes(source);
  next = replaceRequired(
    next,
    'val focusActive = prefs.getBoolean("focus_active", false)',
    'val focusActive = effectiveFocusActive(prefs)',
    'focus-active read',
  );
  next = replaceRequired(
    next,
    'private fun packageIn(pkg: String, json: String): Boolean {',
    `private fun effectiveFocusActive(prefs: android.content.SharedPreferences): Boolean {
    if (!prefs.getBoolean("focus_active", false)) return false
    val state = try { JSONObject(prefs.getString("focus_state", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
    if (!state.optBoolean("active", false)) {
      prefs.edit().putBoolean("focus_active", false).remove("session_blocked_packages").apply()
      return false
    }
    if (state.optString("mode", "timer") != "stopwatch") {
      val endAt = state.optLong("endAt", 0L)
      if (endAt <= 0L || endAt <= System.currentTimeMillis()) {
        state.put("active", false)
        prefs.edit().putBoolean("focus_active", false).putString("focus_state", state.toString()).remove("session_blocked_packages").apply()
        return false
      }
    }
    return true
  }

  private fun packageIn(pkg: String, json: String): Boolean {`,
    'focus-active expiry helper',
  );
  return next;
}

function patchMaintenance(source) {
  let next = fixKotlinRegexes(source);
  next = replaceRequired(next, '.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)', '', 'maintenance network requirement');
  next = replaceRequired(next, '.setPeriodic(6L * 60L * 60L * 1000L)', '.setPeriodic(30L * 60L * 1000L)', 'maintenance period');
  next = replaceRequired(
    next,
    'if (!prefs.getBoolean("updates_enabled", true)) return',
    `if (!prefs.getBoolean("updates_enabled", true)) return
      val now = System.currentTimeMillis()
      val lastCheck = prefs.getLong("last_release_check_at", 0L)
      if (now - lastCheck < 6L * 60L * 60L * 1000L) return
      prefs.edit().putLong("last_release_check_at", now).apply()`,
    'release-check throttle',
  );
  return next;
}

function patchTile(source) {
  return replaceRequired(
    source,
    'val active = getSharedPreferences("go_back_blocking", 0).getBoolean("focus_active", false)',
    `val prefs = getSharedPreferences("go_back_blocking", 0)
    val state = try { org.json.JSONObject(prefs.getString("focus_state", "{}") ?: "{}") } catch (_: Exception) { org.json.JSONObject() }
    val mode = state.optString("mode", "timer")
    val endAt = state.optLong("endAt", 0L)
    val active = state.optBoolean("active", false) && (mode == "stopwatch" || (endAt > 0L && endAt > System.currentTimeMillis()))
    if (!active && prefs.getBoolean("focus_active", false)) prefs.edit().putBoolean("focus_active", false).remove("session_blocked_packages").apply()`,
    'Quick Settings focus state',
  );
}

function patchWidget(source) {
  let next = replaceRequired(
    source,
    'val state = try { JSONObject(context.getSharedPreferences("go_back_blocking", 0).getString("focus_state", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }',
    `val prefs = context.getSharedPreferences("go_back_blocking", 0)
    val state = try { JSONObject(prefs.getString("focus_state", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }`,
    'focus widget preferences',
  );
  next = replaceRequired(
    next,
    'val active = state.optBoolean("active", false)',
    `val modeForState = state.optString("mode", "timer")
    val endAtForState = state.optLong("endAt", 0L)
    val active = state.optBoolean("active", false) && (modeForState == "stopwatch" || (endAtForState > 0L && endAtForState > System.currentTimeMillis()))
    if (!active && prefs.getBoolean("focus_active", false)) prefs.edit().putBoolean("focus_active", false).remove("session_blocked_packages").apply()`,
    'focus widget active state',
  );
  return next;
}

function withGoBackNativeReliability(config) {
  return withDangerousMod(config, ['android', async config => {
    const javaRoot = path.join(config.modRequest.platformProjectRoot, 'app', 'src', 'main', 'java', 'com', 'goback', 'focus');
    const files = {
      module: path.join(javaRoot, 'GoBackBlockingModule.kt'),
      service: path.join(javaRoot, 'GoBackBlockingService.kt'),
      maintenance: path.join(javaRoot, 'GoBackMaintenanceJobService.kt'),
      tile: path.join(javaRoot, 'GoBackFocusTileService.kt'),
      widget: path.join(javaRoot, 'GoBackWidgetProvider.kt'),
    };
    for (const [name, file] of Object.entries(files)) {
      if (!fs.existsSync(file)) throw new Error(`Go Back native reliability: ${name} source was not generated. Ensure withGoBackWidget runs first.`);
    }
    fs.writeFileSync(files.module, fixKotlinRegexes(fs.readFileSync(files.module, 'utf8')));
    fs.writeFileSync(files.service, patchBlockingService(fs.readFileSync(files.service, 'utf8')));
    fs.writeFileSync(files.maintenance, patchMaintenance(fs.readFileSync(files.maintenance, 'utf8')));
    fs.writeFileSync(files.tile, patchTile(fs.readFileSync(files.tile, 'utf8')));
    fs.writeFileSync(files.widget, patchWidget(fs.readFileSync(files.widget, 'utf8')));
    return config;
  }]);
}

module.exports = withGoBackNativeReliability;
