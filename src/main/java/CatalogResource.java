import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.kumuluz.ee.discovery.annotations.DiscoverService;
import com.kumuluz.ee.logs.cdi.Log;
import com.kumuluz.ee.logs.cdi.LogParams;
import io.opentracing.Span;
import io.opentracing.Tracer;
import org.eclipse.microprofile.faulttolerance.*;
import org.eclipse.microprofile.jwt.Claim;
import org.eclipse.microprofile.jwt.ClaimValue;
import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.annotation.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.opentracing.Traced;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.net.URL;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("products")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Log(LogParams.METRICS)
public class CatalogResource {

    @Inject
    private Tracer tracer;

    @Inject
    private ConfigProperties configProperties;

    @Inject
    @Claim("cognito:groups")
    private ClaimValue<Set<String>> groups;

    @Inject
    private JsonWebToken jwt;

//    @Inject
//    @DiscoverService(value = "comment-service", environment = "dev", version = "1.0.0")
//    private Optional<URL> productCommentsUrl;
//
//    @Inject
//    @DiscoverService(value = "cart-service", environment = "dev", version = "1.0.0")
//    private Optional<URL> cartServiceUrl;

    private DynamoDbClient dynamoDB;
    private static final Logger LOGGER = Logger.getLogger(CatalogResource.class.getName());

    @Inject
    @Metric(name = "getProductsHistogram distribution of execution time")
    Histogram getProductsHistogram;

    private volatile String currentRegion;
    private volatile String currentTableName;
    private void checkAndUpdateDynamoDbClient() {
        String newRegion = configProperties.getDynamoRegion();
        if (!newRegion.equals(currentRegion)) {
            try {
                this.dynamoDB = DynamoDbClient.builder()
                        .region(Region.of(newRegion))
                        .build();
                currentRegion = newRegion;
            } catch (Exception e) {
                LOGGER.severe("Error while creating DynamoDB client: " + e.getMessage());
                throw new WebApplicationException("Error while creating DynamoDB client: " + e.getMessage(), e, Response.Status.INTERNAL_SERVER_ERROR);
            }
        }
        currentTableName = configProperties.getTableName();
    }


    @GET
    @Operation(summary = "Get Products", description = "This endpoint allows users to get a list of products.")
    @Parameters({
            @Parameter(name = "searchTerm", description = "Search term for filtering products", in = ParameterIn.QUERY),
            @Parameter(name = "sortBy", description = "Field to sort by", in = ParameterIn.QUERY),
            @Parameter(name = "sortOrder", description = "Sort order", in = ParameterIn.QUERY),
            @Parameter(name = "category", description = "Category for filtering products", in = ParameterIn.QUERY),
            @Parameter(name = "page", description = "Page number", in = ParameterIn.QUERY),
            @Parameter(name = "pageSize", description = "Page size", in = ParameterIn.QUERY)
    })
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Successfully obtained products list."),
            @APIResponse(responseCode = "500", description = "Internal Server Error.")
    })
    @Counted(name = "getProductsCount", description = "Count of getProducts calls")
    @Timed(name = "getProductsTime", description = "Time taken to fetch products")
    @Metered(name = "getProductsMetered", description = "Rate of getProducts calls")
    @ConcurrentGauge(name = "getProductsConcurrent", description = "Concurrent getProducts calls")
    @Timeout(value = 3, unit = ChronoUnit.SECONDS) // Timeout after 5 seconds
    @Retry(maxRetries = 3) // Retry up to 3 times
    @Fallback(fallbackMethod = "getProductsFallback") // Fallback method if all retries fail
    @CircuitBreaker(requestVolumeThreshold = 4) // Use circuit breaker after 4 failed requests
    @Bulkhead(10) // Limit concurrent calls to 10
    @Traced
    public Response getProducts(@QueryParam("searchTerm") String searchTerm,
                                @QueryParam("sortBy") String sortBy,
                                @QueryParam("sortOrder") String sortOrder,
                                @QueryParam("category") String category,
                                @QueryParam("page") Integer page,
                                @QueryParam("pageSize") Integer pageSize) {
        long startTime = System.nanoTime();
        Span span = tracer.buildSpan("getProducts").start();
        Map<String, Object> logMap = new HashMap<>();
        logMap.put("searchTerm", searchTerm);
        logMap.put("sortBy", sortBy);
        logMap.put("sortOrder", sortOrder);
        logMap.put("category", category);
        logMap.put("page", page);
        logMap.put("pageSize", pageSize);
        span.log(logMap);
        checkAndUpdateDynamoDbClient();
        try {
            if (page == null) {
                page = 1;
            }
            if (pageSize == null) {
                pageSize = 4;
            }
            ScanRequest.Builder scanRequestBuilder = ScanRequest.builder().tableName(currentTableName);
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
            span.setTag("completed", true);
            getProductsHistogram.update(endTime - startTime);
            return Response.ok(responseBody).build();

        } catch (DynamoDbException e) {
            long endTime = System.nanoTime();
            getProductsHistogram.update(endTime - startTime);
            LOGGER.log(Level.SEVERE, "Error while getting products ", e);
            span.setTag("error", true);
            throw new WebApplicationException("Error while getting products. Please try again later.", e, Response.Status.INTERNAL_SERVER_ERROR);
        }
        finally {
            span.finish();
        }
    }
    public Response getProductsFallback(@QueryParam("searchTerm") String searchTerm,
                                        @QueryParam("sortBy") String sortBy,
                                        @QueryParam("sortOrder") String sortOrder,
                                        @QueryParam("category") String category,
                                        @QueryParam("page") Integer page,
                                        @QueryParam("pageSize") Integer pageSize) {
        LOGGER.info("Fallback activated: Unable to fetch products at the moment.");
        Map<String, String> response = new HashMap<>();
        response.put("description", "Unable to fetch products at the moment. Please try again later.");
        return Response.ok(response).build();
    }


    @GET
    @Operation(summary = "Get Product Details", description = "This endpoint allows users to get details of a specific product in the catalog by its productId.")
    @Parameters({
            @Parameter(description = "Unique identifier for the product", required = true, example = "a9abe32e-9bd6-43aa-bc00-9044a27b858b")
    })
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Successfully obtained product details.", content = @Content(schema = @Schema(implementation = Product.class))),
            @APIResponse(responseCode = "500", description = "Internal Server Error.")
    })
    @Path("{productId}")
    @Counted(name = "getProductCount", description = "Count of getProduct calls")
    @Timed(name = "getProductTime", description = "Time taken to fetch a product")
    @Metered(name = "getProductMetered", description = "Rate of getProduct calls")
    @ConcurrentGauge(name = "getProductConcurrent", description = "Concurrent getProduct calls")
    @Timeout(value = 20, unit = ChronoUnit.SECONDS) // Timeout after 20 seconds
    @Retry(maxRetries = 3) // Retry up to 3 times
    @Fallback(fallbackMethod = "getProductFallback") // Fallback method if all retries fail
    @CircuitBreaker(requestVolumeThreshold = 4) // Use circuit breaker after 4 failed requests
    @Bulkhead(5) // Limit concurrent calls to 5
    @Traced
    public Response getProduct(
            @PathParam("productId") String productId) {
        Span span = tracer.buildSpan("getProduct").start();
        span.setTag("productId", productId);
        Map<String, Object> logMap = new HashMap<>();
        logMap.put("event", "getProduct");
        logMap.put("value", productId);
        span.log(logMap);
        LOGGER.info("getProduct method called");
        checkAndUpdateDynamoDbClient();

        Map<String, AttributeValue> key = new HashMap<>();
        key.put("productId", AttributeValue.builder().s(productId).build());
        GetItemRequest request;

        try {
            request = GetItemRequest.builder()
                    .key(key)
                    .tableName(currentTableName)
                    .build();
            GetItemResponse getItemResponse = dynamoDB.getItem(request);
            Map<String, AttributeValue> item = getItemResponse.item();
            Map<String, String> transformedItem = ResponseTransformer.transformItem(item);
            span.setTag("completed", true);
            return Response.ok(transformedItem).build();
        } catch (DynamoDbException e) {
            LOGGER.log(Level.SEVERE, "Error while getting product " + productId, e);
            span.setTag("error", true);
            throw new WebApplicationException("Error while getting product. Please try again later.", e, Response.Status.INTERNAL_SERVER_ERROR);
        }
        finally {
        span.finish();
        }
    }

    public Response getProductFallback(@PathParam("productId") String productId) {
        LOGGER.info("Fallback activated: Unable to fetch product at the moment for productId: " + productId);
        Map<String, String> response = new HashMap<>();
        response.put("description", "Unable to fetch product at the moment. Please try again later.");
        return Response.ok(response).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Counted(name = "addProductCount", description = "Count of addProduct calls")
    @Timed(name = "addProductTime", description = "Time taken to add a product")
    @Metered(name = "addProductMetered", description = "Rate of addProduct calls")
    @ConcurrentGauge(name = "addProductConcurrent", description = "Concurrent addProduct calls")
    @Timeout(value = 20, unit = ChronoUnit.SECONDS) // Timeout after 2 seconds
    @Retry(maxRetries = 3) // Retry up to 3 times
    @Fallback(fallbackMethod = "addProductFallback") // Fallback method if all retries fail
    @CircuitBreaker(requestVolumeThreshold = 4) // Use circuit breaker after 4 failed requests
    @Bulkhead(5) // Limit concurrent calls to 5
    @Traced
    public Response addProduct(Product product) {
        if (jwt == null || !groups.getValue().contains("Admins")) {
            return Response.ok("Unauthorized: only admins can add/update products.").build();
        }
        Span span = tracer.buildSpan("addProduct").start();
        span.setTag("productId", product.getProductId());
        Map<String, Object> logMap = new HashMap<>();
        logMap.put("event", "addProduct");
        logMap.put("value", product.getProductName());
        logMap.put("price", product.getPrice());
        logMap.put("discountPrice", product.getDiscountPrice());
        logMap.put("imageURL", product.getImageURL());
        logMap.put("categoryName", product.getCategoryName());
        span.log(logMap);
        LOGGER.info("addProduct method called");
        checkAndUpdateDynamoDbClient();
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
            span.setTag("completed", true);
            return Response.status(Response.Status.CREATED).entity("Product added successfully").build();
        } catch (DynamoDbException e) {
            LOGGER.log(Level.SEVERE, "Error while creating/updating product " + product.getProductId(), e);
            span.setTag("error", true);
            throw new WebApplicationException("Error while creating/updating product. Please try again later.", e, Response.Status.INTERNAL_SERVER_ERROR);
        }
        finally {
        span.finish();
        }
    }
    public Response addProductFallback(Product product) {
        LOGGER.info("Fallback activated: Unable to add/update product at the moment for product name: " + product.getProductName());
        Map<String, String> response = new HashMap<>();
        response.put("description", "Unable to add/update product at the moment. Please try again later.");
        return Response.ok(response).build();
    }


    @PUT
    @Path("/{productId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Counted(name = "updateProductRatingCount", description = "Count of updateProductRating calls")
    @Timed(name = "updateProductRatingTime", description = "Time taken to update a product rating")
    @Metered(name = "updateProductRatingMetered", description = "Rate of updateProductRating calls")
    @ConcurrentGauge(name = "updateProductRatingConcurrent", description = "Concurrent updateProductRating calls")
    @Timeout(value = 20, unit = ChronoUnit.SECONDS) // Timeout after 20 seconds
    @Retry(maxRetries = 3) // Retry up to 3 times
    @Fallback(fallbackMethod = "updateProductRatingFallback") // Fallback method if all retries fail
    @CircuitBreaker(requestVolumeThreshold = 4) // Use circuit breaker after 4 failed requests
    @Bulkhead(5) // Limit concurrent calls to 5
    @Traced
    public Response updateProductRating(@PathParam("productId") String productId,
                                        double avgRating,
                                        @QueryParam("action") String action) {
        if (jwt == null) {
            LOGGER.info("Unauthorized: only authenticated users can add/update rating to product.");
            return Response.ok("Unauthorized: only authenticated users can add/update rating to product.").build();
        }
        Span span = tracer.buildSpan("updateProductRating").start();
        span.setTag("productId", productId);
        Map<String, Object> logMap = new HashMap<>();
        logMap.put("event", "updateProductRating");
        logMap.put("productId", productId);
        logMap.put("avgRating", avgRating);
        logMap.put("action", action);
        logMap.put("groups", groups.getValue());
        logMap.put("email", jwt.getClaim("email"));
        span.log(logMap);
        LOGGER.info("updateProductRating method called");
        checkAndUpdateDynamoDbClient();
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

            UpdateItemRequest updateItemRequest;
                updateItemRequest = UpdateItemRequest.builder()
                        .tableName(currentTableName)
                        .key(key)
                        .attributeUpdates(attributeUpdates)
                        .build();
            dynamoDB.updateItem(updateItemRequest);
            span.setTag("completed", true);
            return Response.ok("Product rating and comment count updated successfully").build();
            } catch (DynamoDbException e) {
                LOGGER.log(Level.SEVERE, "Error while updating product rating and comment count " + productId, e);
                span.setTag("error", true);
                throw new WebApplicationException("Error while updating product rating and comment count. Please try again later.", e, Response.Status.INTERNAL_SERVER_ERROR);
            }
        finally {
            span.finish();
        }
    }
    public Response updateProductRatingFallback(@PathParam("productId") String productId,
                                                double avgRating,
                                                @QueryParam("action") String action) {
        LOGGER.info("Fallback activated: Unable to update product rating and comment count at the moment for productId: " + productId);
        Map<String, String> response = new HashMap<>();
        response.put("description", "Unable to update product rating and comment count at the moment. Please try again later.");
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
    @Traced
    public Response deleteProduct(@PathParam("productId") String productId) {
        if (jwt == null || !groups.getValue().contains("Admins")) {
            LOGGER.info("Unauthorized: only admins can delete products.");
            return Response.ok("Unauthorized: only admins can delete products.").build();
        }
        if (productId == null || productId.isEmpty()) {
            LOGGER.info("Product ID cannot be null or empty.");
            return Response.ok("Product ID cannot be null or empty.").build();
        }
        Span span = tracer.buildSpan("deleteProduct").start();
        span.setTag("productId", productId);
        Map<String, Object> logMap = new HashMap<>();
        logMap.put("event", "deleteProduct");
        logMap.put("productId", productId);
        logMap.put("groups", groups.getValue());
        logMap.put("email", jwt.getClaim("email"));
        span.log(logMap);
        LOGGER.info("deleteProduct method called");
        checkAndUpdateDynamoDbClient();
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("productId", AttributeValue.builder().s(productId).build());

            DeleteItemRequest deleteItemRequest = DeleteItemRequest.builder()
                    .tableName(currentTableName)
                    .key(key)
                    .build();

            dynamoDB.deleteItem(deleteItemRequest);
            span.setTag("completed", true);
            return Response.ok("Product deleted successfully.").build();
        } catch (DynamoDbException e) {
            LOGGER.log(Level.SEVERE, "Error while deleting product " + productId, e);
            span.setTag("error", true);
            throw new WebApplicationException("Error while delete product. Please try again later.", e, Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            span.finish();
        }
    }
    public Response deleteProductFallback(@PathParam("productId") String productId) {
        LOGGER.info("Fallback activated: Unable to delete product at the moment for productId: " + productId);
        Map<String, String> response = new HashMap<>();
        response.put("description", "Unable to delete product at the moment. Please try again later.");
        return Response.ok(response).build();
    }

}