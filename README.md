# AI Devs

Implementation of tasks from the third edition of [AI Devs](https://www.aidevs.pl/).

## Dependencies

- [Tesseract](https://tesseract-ocr.github.io/tessdoc/Installation.html)
```shell
sudo apt install tesseract-ocr
sudo apt install libtesseract-dev
sudo apt install tesseract-ocr-pol # Polish language
```

## Tech stack

- [Kotlin](https://kotlinlang.org/)
- [Spring AI](https://docs.spring.io/spring-ai/reference/index.html)
- [Spring Shell](https://docs.spring.io/spring-shell/reference/index.html)

## Getting started

The project won't work without providing environment variables required
for [application.yaml](./src/main/resources/application.yaml).
Many of them are related to links / credentials provided during the course.

Tasks can be launched by providing in program arguments

```
task <task name>
```

e.g.

```
task 0101
```
