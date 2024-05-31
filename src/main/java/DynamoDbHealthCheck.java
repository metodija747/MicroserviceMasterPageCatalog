import org.eclipse.microprofile.health.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
@Liveness
@Readiness
public class DynamoDbHealthCheck implements HealthCheck {

    private DynamoDbClient dynamoDB;
    private String REQUIRED_TABLE;
    @Inject
    private ConfigProperties configProperties;

    @Override
    public HealthCheckResponse call() {
        this.dynamoDB = DynamoDbClient.builder()
                .region(Region.of(configProperties.getDynamoRegion()))
                .build();

        HealthCheckResponseBuilder responseBuilder = HealthCheckResponse.named("DynamoDB tables health check");
        try {
            ListTablesResponse listTablesResponse = dynamoDB.listTables();
            if (listTablesResponse.hasTableNames() && !listTablesResponse.tableNames().isEmpty()) {
                responseBuilder.up();
                responseBuilder.withData("tableCount", listTablesResponse.tableNames().size());
                responseBuilder.withData("tables", String.join(", ", listTablesResponse.tableNames()));
            } else {
                responseBuilder.down().withData("error", "No tables found");
            }
        } catch (DynamoDbException e) {
            return responseBuilder.down()
                    .withData("error", "Failed to list DynamoDB tables: " + e.getMessage())
                    .build();
        }

        return responseBuilder.build();
    }
}