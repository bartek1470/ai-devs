# AI Devs 3

Implementation of tasks from the third edition of [AI Devs](https://www.aidevs.pl/).

## Structure

Most of the tasks were implemented as a single Spring Shell command.
Those can be found under [directory `simple`](./simple).
Common subprojects configuration done using convention plugins defined in [simple-conventions](./simple-conventions).

## Running

The project won't work without providing environment variables required for properties defined by `application.yaml` and
`application-*.yaml` files.
Many of them are related to links / credentials provided during the course.
Additionally, it's required to install python packages via `pip install -r tools/requirements.txt`.
For tasks requiring neo4j or ollama, there is [docker-compose.yaml](./docker/docker-compose.yaml) with definitions of
those services.

Tasks from [simple](./simple) directory can be can be executed by simply running a main class.
