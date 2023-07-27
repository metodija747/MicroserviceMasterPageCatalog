import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.auth0.jwk.JwkException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.kumuluz.ee.discovery.annotations.DiscoverService;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;

@RequestScoped
@Path("/products")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CatalogResource {

    @Inject
    private ConfigProperties configProperties;

    private DynamoDbClient dynamoDB;
    private static final Logger LOGGER = Logger.getLogger(CatalogResource.class.getName());
//        this.dynamoDB = DynamoDbClient.builder()
//                .region(Region.of(region))
//                .build();
//    @PostConstruct
//    public void init() {

//
//        LOGGER.info("Region: " + region);
//        LOGGER.info("Table Name: " + tableName);
//        LOGGER.info("Issuer: " + issuer);
//

//    }



//    @GET
//    public Response getProducts(@QueryParam("searchTerm") String searchTerm,
//                                @QueryParam("sortBy") String sortBy,
//                                @QueryParam("sortOrder") String sortOrder,
//                                @QueryParam("category") String category,
//                                @QueryParam("page") Integer page,
//                                @QueryParam("pageSize") Integer pageSize) {
////        LOGGER.info("DynamoDB response: " + productCommentsUrl);
//        try {
//            // Default values for page and pageSize if they are not provided
//            if (page == null) {
//                page = 1;
//            }
//            if (pageSize == null) {
//                pageSize = 4;
//            }
//
//            ScanRequest.Builder scanRequestBuilder = ScanRequest.builder().tableName(tableName);
//
//            String filterExpression = "";
//            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
//
//            if (searchTerm != null && !searchTerm.isEmpty()) {
//                filterExpression += "contains(productName, :val)";
//                expressionAttributeValues.put(":val", AttributeValue.builder().s(searchTerm).build());
//            }
//
//            if (category != null && !category.isEmpty()) {
//                if (!filterExpression.isEmpty()) {
//                    filterExpression += " AND ";
//                }
//                filterExpression += "categoryName = :cat";
//                expressionAttributeValues.put(":cat", AttributeValue.builder().s(category).build());
//            }
//
//            if (!filterExpression.isEmpty()) {
//                scanRequestBuilder.filterExpression(filterExpression)
//                        .expressionAttributeValues(expressionAttributeValues);
//            }
//
//            ScanRequest scanRequest = scanRequestBuilder.build();
//            ScanResponse scanResponse = dynamoDB.scan(scanRequest);
//
//            List<Map<String, AttributeValue>> items = scanResponse.items();
//            List<Map<String, AttributeValue>> sortedItems = new ArrayList<>(items);
//
//            if (sortBy != null && !sortBy.isEmpty()) {
//                switch (sortBy) {
//                    case "AverageRating":
//                        sortedItems.sort(Comparator.comparing(item -> Double.parseDouble(item.get("AverageRating").n())));
//                        if (sortOrder != null && sortOrder.equalsIgnoreCase("DSC")) Collections.reverse(sortedItems);
//                        items = sortedItems;
//                        break;
//                    case "Price":
//                        sortedItems.sort(Comparator.comparing(item -> Double.parseDouble(item.get("Price").n())));
//                        if (sortOrder != null && sortOrder.equalsIgnoreCase("DSC")) Collections.reverse(sortedItems);
//                        items = sortedItems;
//                        break;
//                    default:
//                        break;
//                }
//            }
//
//            int totalPages = (int) Math.ceil((double) items.size() / pageSize);
//
//            int start = (page - 1) * pageSize;
//            int end = Math.min(start + pageSize, items.size());
//            List<Map<String, AttributeValue>> pagedItems = items.subList(start, end);
//
//            List<Map<String, String>> itemsString = ResponseTransformer.transformItems(pagedItems);
//            Map<String, Object> responseBody = new HashMap<>();
//            responseBody.put("products", itemsString);
//            responseBody.put("totalPages", totalPages);
//            responseBody.put("totalProducts", items.size());
//            responseBody.put("currentRangeStart", start + 1);
//            responseBody.put("currentRangeEnd", end);
//
//            return Response.ok(responseBody).build();
//
//        } catch (DynamoDbException e) {
//            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
//        }
//    }

    @GET
    @Path("/{productId}")
    public Response getProduct(@PathParam("productId") String productId) {
//        LOGGER.info(configProperties.getIssuer() + configProperties.getTableName() + configProperties.getDynamoDbRegion());
//        LOGGER.info("Issuer: " + issuer);
//        LOGGER.info("Table Name: " + tableName);
//        LOGGER.info("Region: " + region);
//        Map<String, AttributeValue> key = new HashMap<>();
//        key.put("productId", AttributeValue.builder().s(productId).build());

//        GetItemRequest request = GetItemRequest.builder()
//                .key(key)
//                .tableName(tableName)
//                .build();
//
        try {
//            GetItemResponse getItemResponse = dynamoDB.getItem(request);
//            Map<String, AttributeValue> item = getItemResponse.item();
//            Map<String, String> transformedItem = ResponseTransformer.transformItem(item);

            return Response.ok().build();
        } catch (DynamoDbException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }


//    @POST
//    @Consumes(MediaType.APPLICATION_JSON)
//    public Response addProduct(@HeaderParam("Auth") String token, Product product) {
//        // Parse the token from the Authorization header
//        LOGGER.info("DynamoDB response: " + token);
//
////         Verify the token and get the user's groups
//        List<String> groups = null;
//        try {
//            TokenVerifier.verifyToken(token, issuer);
//            groups = TokenVerifier.getGroups(token, issuer);
//        } catch (JWTVerificationException | JwkException | MalformedURLException e) {
//            return Response.status(Response.Status.FORBIDDEN).entity("Invalid token.").build();
//        }
//
//        // Check if the user is in the "Admins" group
//        if (groups == null || !groups.contains("Admins")) {
//            return Response.status(Response.Status.FORBIDDEN).entity("Unauthorized: only admin users can add new products.").build();
//        }
//        try {
//            Map<String, AttributeValue> item = new HashMap<>();
//            item.put("productId", AttributeValue.builder().s(product.getProductId()).build());
//            item.put("AverageRating", AttributeValue.builder().n(Double.toString(product.getAverageRating())).build());
//            item.put("categoryName", AttributeValue.builder().s(product.getCategoryName()).build());
//            item.put("imageURL", AttributeValue.builder().s(product.getImageURL()).build());
//            item.put("Price", AttributeValue.builder().n(Double.toString(product.getPrice())).build());
//            item.put("productName", AttributeValue.builder().s(product.getProductName()).build());
//            item.put("Description", AttributeValue.builder().s(product.getDescription()).build());
//            item.put("commentsCount", AttributeValue.builder().n(Integer.toString(product.getCommentsCount())).build());
//            item.put("discountPrice", AttributeValue.builder().n(Double.toString(product.getDiscountPrice())).build());
//
//            PutItemRequest putItemRequest = PutItemRequest.builder()
//                    .tableName(tableName)
//                    .item(item)
//                    .build();
//
//            dynamoDB.putItem(putItemRequest);
//
//            return Response.status(Response.Status.CREATED).entity("Product added successfully").build();
//        } catch (DynamoDbException e) {
//            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
//        }
//    }
//
//
//    @PUT
//    @Path("/{productId}")
//    @Consumes(MediaType.APPLICATION_JSON)
//    public Response updateProductRating(@PathParam("productId") String productId,
//                                        @HeaderParam("Auth") String token,
//                                        double avgRating,
//                                        @QueryParam("action") String action) {
//        // Parse the token from the Authorization header
//        LOGGER.info("DynamoDB response: " + token);
//        LOGGER.info("DynamoDB response: " + avgRating);
//        LOGGER.info("DynamoDB response: " + productId);
//        LOGGER.info("DynamoDB response: " + action);
//
//        // Verify the token and get the  user's groups
//        List<String> groups = null;
//        try {
//            TokenVerifier.verifyToken(token, issuer);
//            groups = TokenVerifier.getGroups(token, issuer);
//        } catch (JWTVerificationException | JwkException | MalformedURLException e) {
//            return Response.status(Response.Status.FORBIDDEN).entity("Invalid token.").build();
//        }
//
//        // Check if the user is in the "Admins" group
//        if (groups == null || !groups.contains("Admins")) {
//            return Response.status(Response.Status.FORBIDDEN).entity("Unauthorized: only admin users can update product ratings.").build();
//        }
//
//        try {
//            Map<String, AttributeValue> key = new HashMap<>();
//            key.put("productId", AttributeValue.builder().s(productId).build());
//
//            Map<String, AttributeValueUpdate> attributeUpdates = new HashMap<>();
//            attributeUpdates.put("AverageRating", AttributeValueUpdate.builder()
//                    .value(AttributeValue.builder().n(String.valueOf(avgRating)).build())
//                    .action(AttributeAction.PUT)
//                    .build());
//
//            Map<String, String> updateValues = new HashMap<>();
//            updateValues.put("add", "1");
//            updateValues.put("delete", "-1");
//            updateValues.put("zero", "0");
//            String updateValue = updateValues.containsKey(action) ? updateValues.get(action) : "0";
//            attributeUpdates.put("commentsCount", AttributeValueUpdate.builder()
//                    .value(AttributeValue.builder().n(updateValue).build())
//                    .action(AttributeAction.ADD)
//                    .build());
//
//            UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
//                    .tableName(tableName)
//                    .key(key)
//                    .attributeUpdates(attributeUpdates)
//                    .build();
//
//            dynamoDB.updateItem(updateItemRequest);
//
//            return Response.ok("Product rating and comment count updated successfully").build();
//        } catch (DynamoDbException e) {
//            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
//        }
//    }
//
//
//
//    @DELETE
//    @Path("/{productId}")
//    public Response deleteProduct(@PathParam("productId") String productId, @HeaderParam("Auth") String token) {
//        try {
//            List<String> groups;
//            try {
//                groups = TokenVerifier.getGroups(token, issuer);
//            } catch (JwkException | MalformedURLException e) {
//                return Response.status(Response.Status.UNAUTHORIZED).entity("Invalid token.").build();
//            }
//            if (groups == null || !groups.contains("Admins")) {
//                return Response.status(Response.Status.FORBIDDEN).entity("Unauthorized: only admin users can delete products.").build();
//            }
//
//            Map<String, AttributeValue> key = new HashMap<>();
//            key.put("productId", AttributeValue.builder().s(productId).build());
//
//            DeleteItemRequest deleteItemRequest = DeleteItemRequest.builder()
//                    .tableName(tableName)
//                    .key(key)
//                    .build();
//
//            dynamoDB.deleteItem(deleteItemRequest);
//
//            return Response.ok("Product deleted successfully.").build();
//        } catch (DynamoDbException e) {
//            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
//        }
//    }

}