## LavaMenu (Fabric, client-only)

Клиентский мод быстрых команд LavaWin (MC 26.2).  
Канон проекта: родительская папка `~/Desktop/LavaMenu/` (здесь только Gradle-исходники).

### Управление
- **R** — основное меню
- **G** — быстрое меню  
Клавиши только в Minecraft → Управление; в моде — режим удержания и слоты G.

### Сборка
```bash
export JAVA_HOME="/usr/local/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home"
./gradlew build
```
JAR: `build/libs/lavamenu-0.2.0.jar`

Конфиг: `.minecraft/config/lavamenu.json`
