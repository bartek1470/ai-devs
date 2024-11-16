FROM ubuntu:noble

RUN apt update  \
    && apt upgrade -y \
    && apt install -y software-properties-common \
    && add-apt-repository ppa:deadsnakes/ppa \
    && apt install -y \
    ffmpeg \
    python3.9 \
    python3-pip \
    pipx \
    && add-apt-repository -r ppa:deadsnakes/ppa \
    && apt remove -y software-properties-common \
    && apt autoremove -y \
    && apt clean

RUN pipx install openai-whisper
ENV PATH=$PATH:/root/.local/bin

WORKDIR /app

CMD ["whisper"]
