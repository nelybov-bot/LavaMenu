# LavaMenu

Клиентский Fabric-мод для сервера LavaWin (Minecraft Java **26.1.x**).

## Установка

Скопируйте `release/lavamenu-0.1.0.jar` в `.minecraft/mods/`.

- Конфиг: `.minecraft/config/lavamenu.json`
- Чаты: `.minecraft/config/lavamenu-chats.json`
- Головы: `.minecraft/config/lavamenu/faces/`
- Иконка центра G (опционально): `.minecraft/config/lavamenu/radial_center.png` (64×64 PNG)

## Сборка

Нужна **Java 25**.

```bash
cd source
export JAVA_HOME="/usr/local/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home"
./gradlew build
```

Готовый JAR: `source/build/libs/lavamenu-0.1.0.jar`

## Управление

- **R** — основное меню (Точки / Команды / Друзья / Чаты / Настройки)
- **G** — быстрое круговое меню
- Клавиши меняются в Minecraft → Управление; в моде — режим удержания и слоты колеса

Лицензия: MIT (см. `LICENSE`).
