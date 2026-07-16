FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY core core
COPY server server
COPY desktop desktop
RUN ./mvnw --batch-mode --no-transfer-progress \
    -pl server/pdf-batch-server -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN useradd --system --uid 10001 --home-dir /var/lib/pdf-batch pdfbatch \
    && mkdir -p /var/lib/pdf-batch/jobs \
    && chown -R pdfbatch:pdfbatch /var/lib/pdf-batch
USER 10001
WORKDIR /opt/pdf-batch
COPY --from=build /workspace/server/pdf-batch-server/target/pdf-batch-server-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
