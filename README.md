# 📱 QR Scanner for Factory

**Промышленное Android-приложение для сканирования, верификации и учёта QR-кодов на производстве.**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-blue.svg)](https://kotlinlang.org/)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://android-arsenal.com/api?level=24)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

---

## 🎯 О проекте

Приложение разработано по контракту для промышленного завода. Оно позволяет:

- ✅ Сканировать QR-коды готовой продукции
- ✅ Извлекать 17 параметров (толщина, ширина, длина, марка стали, масса и др.)
- ✅ Проверять дубликаты — повторное сканирование не создаёт запись
- ✅ Сохранять данные в CSV-файл
- ✅ Экспортировать CSV через шеринг (Telegram, почта, облако)
- ✅ Работать полностью офлайн

---

## 🛠 Технологии

| Компонент | Технология |
|-----------|------------|
| Язык | Kotlin |
| Камера | CameraX |
| Распознавание QR | ML Kit Barcode Scanner |
| Хранение данных | Кастомный CSV-менеджер |
| Шеринг файлов | FileProvider |
| Асинхронность | Coroutines + Handler |
| UI | XML + кастомные анимации |
| Минимальная версия | Android 7.0 (API 24) |
