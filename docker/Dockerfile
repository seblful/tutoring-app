# The Android toolchain the project builds with — JDK 21 and the SDK packages AGP asks for — as an image,
# so a machine needs Docker and nothing else. Used through `compose.yaml` at the repo root; see the README.
#
# Building the image accepts the Android SDK licenses on the builder's behalf, which is what Android
# Studio's first run and the CI runner's preinstalled SDK both do.
FROM eclipse-temurin:21-jdk-noble

# The command-line tools are only the installer; the versions that matter are the packages below, which
# are pinned to what `app/build.gradle.kts` compiles against so AGP never downloads into a throwaway layer.
ARG CMDLINE_TOOLS=13114758
ARG PLATFORM=android-37.0
ARG BUILD_TOOLS=37.0.0

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip git \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL -o /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS}_latest.zip" \
    && mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_HOME}/cmdline-tools" \
    && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip

RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager --install "platform-tools" "platforms;${PLATFORM}" "build-tools;${BUILD_TOOLS}"

WORKDIR /workspace
ENTRYPOINT ["./gradlew"]
CMD ["tasks"]
