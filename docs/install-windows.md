# Installing on Windows 11

WheelLab needs **Java 17** (or newer), **Node.js 20** (or newer) and **Git**. Maven is **not** needed: the
Maven Wrapper in `server/` downloads it the first time you build.

## 1. Install the tools

Open **PowerShell** (Start → type "PowerShell") and run:

```powershell
winget install EclipseAdoptium.Temurin.17.JDK OpenJS.NodeJS.LTS Git.Git
```

Accept the prompts. Then **close PowerShell and open a new one**, so it sees the new programs, and check:

```powershell
java -version      # should say 17 (or newer)
node -v            # v20 or newer
git --version
```

If `java` is not found, reinstall the JDK from https://adoptium.net and, in the installer, turn on
**"Set JAVA_HOME variable"** and **"Add to PATH"**.

## 2. Download and build

```powershell
cd $HOME\Documents
git clone https://github.com/ckokolog-ship-it/WheelLab.git
cd WheelLab\web
npm install
npm run build
cd ..\server
.\mvnw.cmd package
```

The first `.\mvnw.cmd` downloads Maven (about 10 MB) and the libraries; later builds are quick.

## 3. Run

```powershell
java -jar target\wheellab-server.jar --web ..\web\dist
```

Open **http://localhost:8090** in your browser. Stop the server with `Ctrl+C`. If Windows Firewall asks,
you can deny network access -- the app only needs your own computer.

## Updating

```powershell
cd $HOME\Documents\WheelLab
git pull
cd web ; npm install ; npm run build
cd ..\server ; .\mvnw.cmd package
```

## If you prefer to install Maven itself

`winget install Apache.Maven` (if winget finds it), or download the "Binary zip archive" from
https://maven.apache.org/download.cgi, unzip it (e.g. to `C:\tools\apache-maven`), add its `bin` folder to
**Path** (Start → "Edit the system environment variables" → Environment Variables → Path → New), open a new
PowerShell and check `mvn -v`. Then `mvn package` works the same as `.\mvnw.cmd package`.

## Troubleshooting

| Problem | Fix |
|---|---|
| `mvnw.cmd` says JAVA_HOME is not set | set it: `setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-17..."` (your JDK folder), then open a new PowerShell |
| `.\mvnw.cmd` "cannot be loaded because running scripts is disabled" | run it from Command Prompt (`cmd`) instead, or `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` |
| Port 8090 is in use | add `--port 9000` and open http://localhost:9000 |
| The page says it cannot reach the server | the server window must stay open while you use the app |
