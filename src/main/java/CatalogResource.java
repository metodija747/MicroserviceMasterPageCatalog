import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.auth0.jwk.JwkException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.google.gson.JsonArray;
import com.kumuluz.ee.cors.annotations.CrossOrigin;
import com.kumuluz.ee.discovery.annotations.DiscoverService;
import com.kumuluz.ee.logs.cdi.Log;
import com.kumuluz.ee.logs.cdi.LogParams;
import org.eclipse.microprofile.faulttolerance.*;
import org.eclipse.microprofile.jwt.Claim;
import org.eclipse.microprofile.jwt.ClaimValue;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.annotation.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Logger;

@Path("/products")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Log(LogParams.METRICS)
public class CatalogResource {

    @Inject
    private ConfigProperties configProperties;

    @Inject
    private JsonWebToken jwt;

    @Inject
    @DiscoverService(value = "comment-service", environment = "dev", version = "1.0.0")
    private Optional<URL> productCommentsUrl;

    @Inject
    @DiscoverService(value = "cart-service", environment = "dev", version = "1.0.0")
    private Optional<URL> cartServiceUrl;

    private DynamoDbClient dynamoDB;
    private static final Logger LOGGER = Logger.getLogger(CatalogResource.class.getName());

    @Inject
    @Metric(name = "getProductsHistogram distribution of execution time")
    Histogram getProductsHistogram;

    @GET
    @Path("/{productId}")
    @Counted(name = "getProductCount", description = "Count of getProduct calls")
    @Timed(name = "getProductTime", description = "Time taken to fetch a product")
    @Metered(name = "getProductMetered", description = "Rate of getProduct calls")
    @ConcurrentGauge(name = "getProductConcurrent", description = "Concurrent getProduct calls")
    @Timeout(value = 2, unit = ChronoUnit.SECONDS) // Timeout after 2 seconds
    @Retry(maxRetries = 3) // Retry up to 3 times
    @Fallback(fallbackMethod = "getProductFallback") // Fallback method if all retries fail
    @CircuitBreaker(requestVolumeThreshold = 4) // Use circuit breaker after 4 failed requests
    @Bulkhead(5) // Limit concurrent calls to 5
    public Response getProduct(@PathParam("productId") String productId) {

        this.dynamoDB = DynamoDbClient.builder()
                .region(Region.of(configProperties.getDynamoRegion()))
                .build();
        LOGGER.info(configProperties.getCognitoIssuer() + configProperties.getTableName() + configProperties.getDynamoRegion());
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("productId", AttributeValue.builder().s(productId).build());

        GetItemRequest request = GetItemRequest.builder()
                .key(key)
                .tableName(configProperties.getTableName())
                .build();

        try {
            GetItemResponse getItemResponse = dynamoDB.getItem(request);
            Map<String, AttributeValue> item = getItemResponse.item();
            Map<String, String> transformedItem = ResponseTransformer.transformItem(item);

            return Response.ok(transformedItem).build();
        } catch (DynamoDbException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    public Response getProductFallback(@PathParam("productId") String productId) {
        // Log the fallback
        LOGGER.info("Details are not available at the moment for productId: " + productId);
        // Create a default response
        Map<String, String> response = new HashMap<>();
        response.put("description", "Details are not available at the moment for productId: " + productId);

        // Return the default product
        return Response.ok(response).build();
    }



    @GET
    @Counted(name = "getProductsCount", description = "Count of getProducts calls")
    @Timed(name = "getProductsTime", description = "Time taken to fetch products")
    @Metered(name = "getProductsMetered", description = "Rate of getProducts calls")
    @ConcurrentGauge(name = "getProductsConcurrent", description = "Concurrent getProducts calls")
    @Timeout(value = 5, unit = ChronoUnit.SECONDS) // Timeout after 5 seconds
    @Retry(maxRetries = 3) // Retry up to 3 times
    @Fallback(fallbackMethod = "getProductsFallback") // Fallback method if all retries fail
    @CircuitBreaker(requestVolumeThreshold = 4) // Use circuit breaker after 4 failed requests
    @Bulkhead(10) // Limit concurrent calls to 10
    public Response getProducts(@QueryParam("searchTerm") String searchTerm,
                                @QueryParam("sortBy") String sortBy,
                                @QueryParam("sortOrder") String sortOrder,
                                @QueryParam("category") String category,
                                @QueryParam("page") Integer page,
                                @QueryParam("pageSize") Integer pageSize) {
        long startTime = System.nanoTime();
        this.dynamoDB = DynamoDbClient.builder()
                .region(Region.of(configProperties.getDynamoRegion()))
                .build();
//        LOGGER.info("DynamoDB response: " + productCommentsUrl);
        try {
            // Default values for page and pageSize if they are not provided
            if (page == null) {
                page = 1;
            }
            if (pageSize == null) {
                pageSize = 4;
            }

            ScanRequest.Builder scanRequestBuilder = ScanRequest.builder().tableName(configProperties.getTableName());

            String filterExpression = "";
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();

            if (searchTerm != null && !searchTerm.isEmpty()) {
                filterExpression += "contains(productName, :val)";
                expressionAttributeValues.put(":val", AttributeValue.builder().s(searchTerm).build());
            }

            if (category != null && !category.isEmpty()) {
                if (!filterExpression.isEmpty()) {
                    filterExpression += " AND ";
                }
                filterExpression += "categoryName = :cat";
                expressionAttributeValues.put(":cat", AttributeValue.builder().s(category).build());
            }

            if (!filterExpression.isEmpty()) {
                scanRequestBuilder.filterExpression(filterExpression)
                        .expressionAttributeValues(expressionAttributeValues);
            }

            ScanRequest scanRequest = scanRequestBuilder.build();
            ScanResponse scanResponse = dynamoDB.scan(scanRequest);

            List<Map<String, AttributeValue>> items = scanResponse.items();
            List<Map<String, AttributeValue>> sortedItems = new ArrayList<>(items);

            if (sortBy != null && !sortBy.isEmpty()) {
                switch (sortBy) {
                    case "AverageRating":
                        sortedItems.sort(Comparator.comparing(item -> Double.parseDouble(item.get("AverageRating").n())));
                        if (sortOrder != null && sortOrder.equalsIgnoreCase("DSC")) Collections.reverse(sortedItems);
                        items = sortedItems;
                        break;
                    case "Price":
                        sortedItems.sort(Comparator.comparing(item -> Double.parseDouble(item.get("Price").n())));
                        if (sortOrder != null && sortOrder.equalsIgnoreCase("DSC")) Collections.reverse(sortedItems);
                        items = sortedItems;
                        break;
                    default:
                        break;
                }
            }

            int totalPages = (int) Math.ceil((double) items.size() / pageSize);

            int start = (page - 1) * pageSize;
            int end = Math.min(start + pageSize, items.size());
            List<Map<String, AttributeValue>> pagedItems = items.subList(start, end);

            List<Map<String, String>> itemsString = ResponseTransformer.transformItems(pagedItems);
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("products", itemsString);
            responseBody.put("totalPages", totalPages);
            responseBody.put("totalProducts", items.size());
            responseBody.put("currentRangeStart", start + 1);
            responseBody.put("currentRangeEnd", end);
            long endTime = System.nanoTime();
            getProductsHistogram.update(endTime - startTime);
            return Response.ok(responseBody).build();

        } catch (DynamoDbException e) {
            long endTime = System.nanoTime();
            getProductsHistogram.update(endTime - startTime);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }
    public Response getProductsFallback(@QueryParam("searchTerm") String searchTerm,
                                        @QueryParam("sortBy") String sortBy,
                                        @QueryParam("sortOrder") String sortOrder,
                                        @QueryParam("category") String category,
                                        @QueryParam("page") Integer page,
                                        @QueryParam("pageSize") Integer pageSize) {
        // Log the fallback
        LOGGER.info("Unable to fetch products at the moment.");

        // Create a default response
        Map<String, String> response = new HashMap<>();
        response.put("description", "Unable to fetch products at the moment. Please try again later.");

        // Return the default response
        return Response.ok(response).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Counted(name = "addProductCount", description = "Count of addProduct calls")
    @Timed(name = "addProductTime", description = "Time taken to add a product")
    @Metered(name = "addProductMetered", description = "Rate of addProduct calls")
    @ConcurrentGauge(name = "addProductConcurrent", description = "Concurrent addProduct calls")
    @Timeout(value = 2, unit = ChronoUnit.SECONDS) // Timeout after 2 seconds
    @Retry(maxRetries = 3) // Retry up to 3 times
    @Fallback(fallbackMethod = "addProductFallback") // Fallback method if all retries fail
    @CircuitBreaker(requestVolumeThreshold = 4) // Use circuit breaker after 4 failed requests
    @Bulkhead(5) // Limit concurrent calls to 5
    public Response addProduct(Product product) {
        if (jwt == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Unauthorized: only authenticated users can update product ratings.").build();
        }
        this.dynamoDB = DynamoDbClient.builder()
                .region(Region.of(configProperties.getDynamoRegion()))
                .build();
        try {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("productId", AttributeValue.builder().s(product.getProductId()).build());
            item.put("AverageRating", AttributeValue.builder().n(Double.toString(product.getAverageRating())).build());
            item.put("categoryName", AttributeValue.builder().s(product.getCategoryName()).build());
            item.put("imageURL", AttributeValue.builder().s(product.getImageURL()).build());
            item.put("Price", AttributeValue.builder().n(Double.toString(product.getPrice())).build());
            item.put("productName", AttributeValue.builder().s(product.getProductName()).build());
            item.put("Description", AttributeValue.builder().s(product.getDescription()).build());
            item.put("commentsCount", AttributeValue.builder().n(Integer.toString(product.getCommentsCount())).build());
            item.put("discountPrice", AttributeValue.builder().n(Double.toString(product.getDiscountPrice())).build());

            PutItemRequest putItemRequest = PutItemRequest.builder()
                    .tableName(configProperties.getTableName())
                    .item(item)
                    .build();

            dynamoDB.putItem(putItemRequest);

            return Response.status(Response.Status.CREATED).entity("Product added successfully").build();
        } catch (DynamoDbException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }
    public Response addProductFallback(Product product) {
        // Log the fallback
        LOGGER.info("Unable to add product at the moment for product: " + product.getProductName());

        // Create a default response
        Map<String, String> response = new HashMap<>();
        response.put("description", "Unable to add product at the moment for product: " + product.getProductName());

        // Return the default response
        return Response.ok(response).build();
    }


    @PUT
    @Path("/{productId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Counted(name = "updateProductRatingCount", description = "Count of updateProductRating calls")
    @Timed(name = "updateProductRatingTime", description = "Time taken to update a product rating")
    @Metered(name = "updateProductRatingMetered", description = "Rate of updateProductRating calls")
    @ConcurrentGauge(name = "updateProductRatingConcurrent", description = "Concurrent updateProductRating calls")
    @Timeout(value = 2, unit = ChronoUnit.SECONDS) // Timeout after 2 seconds
    @Retry(maxRetries = 3) // Retry up to 3 times
    @Fallback(fallbackMethod = "updateProductRatingFallback") // Fallback method if all retries fail
    @CircuitBreaker(requestVolumeThreshold = 4) // Use circuit breaker after 4 failed requests
    @Bulkhead(5) // Limit concurrent calls to 5
    public Response updateProductRating(@PathParam("productId") String productId,
                                        double avgRating,
                                        @QueryParam("action") String action) {
        if (jwt == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Unauthorized: only authenticated users can update product ratings.").build();
        }
        this.dynamoDB = DynamoDbClient.builder()
                .region(Region.of(configProperties.getDynamoRegion()))
                .build();


        LOGGER.info("DynamoDB response: " + avgRating);
        LOGGER.info("DynamoDB response: " + productId);
        LOGGER.info("DynamoDB response: " + action);


        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("productId", AttributeValue.builder().s(productId).build());

            Map<String, AttributeValueUpdate> attributeUpdates = new HashMap<>();
            attributeUpdates.put("AverageRating", AttributeValueUpdate.builder()
                    .value(AttributeValue.builder().n(String.valueOf(avgRating)).build())
                    .action(AttributeAction.PUT)
                    .build());

            Map<String, String> updateValues = new HashMap<>();
            updateValues.put("add", "1");
            updateValues.put("delete", "-1");
            updateValues.put("zero", "0");
            String updateValue = updateValues.containsKey(action) ? updateValues.get(action) : "0";
            attributeUpdates.put("commentsCount", AttributeValueUpdate.builder()
                    .value(AttributeValue.builder().n(updateValue).build())
                    .action(AttributeAction.ADD)
                    .build());

            UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                    .tableName(configProperties.getTableName())
                    .key(key)
                    .attributeUpdates(attributeUpdates)
                    .build();

            dynamoDB.updateItem(updateItemRequest);

            return Response.ok("Product rating and comment count updated successfully").build();
        } catch (DynamoDbException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }
    public Response updateProductRatingFallback(@PathParam("productId") String productId,
                                                double avgRating,
                                                @QueryParam("action") String action) {
        // Log the fallback
        LOGGER.info("Unable to update product rating at the moment for productId: " + productId);

        // Create a default response
        Map<String, String> response = new HashMap<>();
        response.put("description", "Unable to update product rating at the moment for productId: " + productId);

        // Return the default response
        return Response.ok(response).build();
    }



    @DELETE
    @Path("/{productId}")
    @Counted(name = "deleteProductCount", description = "Count of deleteProduct calls")
    @Timed(name = "deleteProductTime", description = "Time taken to delete a product")
    @Metered(name = "deleteProductMetered", description = "Rate of deleteProduct calls")
    @ConcurrentGauge(name = "deleteProductConcurrent", description = "Concurrent deleteProduct calls")
    @Timeout(value = 2, unit = ChronoUnit.SECONDS) // Timeout after 2 seconds
    @Retry(maxRetries = 3) // Retry up to 3 times
    @Fallback(fallbackMethod = "deleteProductFallback") // Fallback method if all retries fail
    @CircuitBreaker(requestVolumeThreshold = 4) // Use circuit breaker after 4 failed requests
    @Bulkhead(5) // Limit concurrent calls to 5
    public Response deleteProduct(@PathParam("productId") String productId) {
        try {
            if (jwt == null) {
                return Response.status(Response.Status.UNAUTHORIZED).entity("Unauthorized: only authenticated users can update product ratings.").build();
            }

            Map<String, AttributeValue> key = new HashMap<>();
            key.put("productId", AttributeValue.builder().s(productId).build());

            DeleteItemRequest deleteItemRequest = DeleteItemRequest.builder()
                    .tableName(configProperties.getTableName())
                    .key(key)
                    .build();

            dynamoDB.deleteItem(deleteItemRequest);

            return Response.ok("Product deleted successfully.").build();
        } catch (DynamoDbException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    public Response deleteProductFallback(@PathParam("productId") String productId) {
        // Log the fallback
        LOGGER.info("Unable to delete product at the moment for productId: " + productId);

        // Create a default response
        Map<String, String> response = new HashMap<>();
        response.put("description", "Unable to delete product at the moment for productId: " + productId);

        // Return the default response
        return Response.ok(response).build();
    }

}