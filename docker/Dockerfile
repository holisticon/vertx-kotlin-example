FROM azul/zulu-openjdk-alpine:12
### Setup user for build execution and application runtime
ENV APP_ROOT=/opt/app-root
RUN mkdir ${APP_ROOT}
RUN mkdir ${APP_ROOT}/bin
ENV PATH=${APP_ROOT}/bin:${PATH} HOME=${APP_ROOT}
RUN chmod -R u+x ${APP_ROOT}/bin && \
    chgrp -R 0 ${APP_ROOT} && \
    chmod -R g=u ${APP_ROOT} /etc/passwd

### Containers should NOT run as root as a good practice
USER 10001
WORKDIR ${APP_ROOT}
