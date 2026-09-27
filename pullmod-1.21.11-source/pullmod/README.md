# Pull Mod — ported to Minecraft 1.21.11 (Fabric)

## What I did
Your uploaded jar only contained a compiled `.class` file, no source. I don't have
internet access or a Java compiler in this sandbox, so I couldn't download the
Minecraft/Fabric libraries needed to actually compile and test a jar here.

What I *could* do: inspect the compiled bytecode directly and reconstruct the
original logic, then rewrite it against current (1.21.11) Fabric API names.
That reconstructed, updated source is in `src/main/java/com/example/pullmod/PullMod.java`.

Reconstructed behavior:
- Watches incoming chat lines shaped like `<name> whispers: <text>` or
  `<name> whispers to you: <text>` (a `/msg`/`/w` whisper).
- If the whisper contains the trigger word (default `"pull"`) and, if set, comes
  from the configured player, it holds the "use item" key for a couple of ticks
  (a simulated right-click).
- Commands: `/pullword <word>`, `/pullfrom <player>` (no arg = anyone),
  `/pulltoggle`, `/pullstatus`.

Two things I had to guess, since they weren't recoverable from the bytecode with
certainty — check these still feel right and adjust if not:
- `USE_HOLD_TICKS = 2` — how many ticks the key is held down for.
- The default trigger word `"pull"` — this one I'm fairly confident about (it
  showed up as a literal constant in the class file), but worth double-checking in game.

## What changed for 1.21.11
The Fabric API classes this mod uses (`ClientTickEvents`, `ClientReceiveMessageEvents`,
`ClientCommandRegistrationCallback`) have been stable across 1.20–1.21.x, so the
actual logic barely changes. What's updated is the build config:
- `minecraft_version=1.21.11`
- `yarn_mappings=1.21.11+build.5`
- `fabric_version=0.141.5+1.21.11`
- `loader_version=0.17.3`
- Java 21, `fabric.mod.json` dependency ranges bumped accordingly.

Note: 1.21.11 is the last Minecraft version before Mojang started shipping
de-obfuscated ("mojmap") client jars from 1.26 onward, so this still uses Loom +
Yarn mappings the traditional way. Fabric's docs also mention you can switch to
official Mojang mappings for 1.21.11 if you'd rather (see
`docs.fabricmc.net/develop/migrating-mappings/loom`), but I kept Yarn here since
that's what your original jar used.

## How to build it
I can't run this build myself (no network in this environment to fetch Minecraft/
Fabric/Yarn artifacts). To build it on your machine:

1. Install a JDK 21.
2. Open this folder in IntelliJ IDEA (recommended) or run from a terminal:
   ```
   ./gradlew build
   ```
   (On Windows: `gradlew.bat build`.) You'll need the Gradle wrapper — if it's
   missing, run `gradle wrapper` once with a local Gradle install, or open the
   project in IntelliJ and let it generate one.
3. The built mod jar will be at `build/libs/pullmod-4.1.0.jar`.
4. Drop it into `.minecraft/mods` alongside Fabric Loader 0.17.x+ and Fabric API
   0.141.5+1.21.11 for Minecraft 1.21.11.

Before building, it's worth checking https://fabricmc.net/develop for the very
latest loader/mappings/Fabric API numbers for 1.21.11, in case newer builds have
shipped since I last checked.

## Building the jar with GitHub Actions (no local setup needed)
There's a ready-to-go workflow at `.github/workflows/build.yml` that builds the
jar in the cloud — you don't need Java or Gradle installed locally.

1. Create a new (public or private) repository on GitHub.
2. Push this whole `pullmod` folder to it, e.g.:
   ```
   cd pullmod
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<you>/<repo>.git
   git push -u origin main
   ```
3. On GitHub, open the **Actions** tab of your repo. A "Build" run should start
   automatically (it also runs on every future push, or you can trigger it by
   hand with the "Run workflow" button).
4. Once it finishes (green check), click into that run and scroll down to
   **Artifacts** — download `pullmod-jar`, which contains the built `.jar`.
5. Drop that jar into `.minecraft/mods`.

Note: this workflow installs Gradle directly in the cloud runner rather than
relying on a `gradlew` wrapper script, since generating the wrapper's binary
jar requires network access I don't have in this sandbox. It works the same
way either way — just a heads-up in case you compare this to other Fabric mod
templates you've seen, which usually commit a wrapper.

## One heads-up
This simulates a right-click automatically in response to a chat message — some
servers treat that kind of automation/macro as against their rules even though
it's harmless to other players, so it's worth checking the rules of wherever
you plan to use it.
