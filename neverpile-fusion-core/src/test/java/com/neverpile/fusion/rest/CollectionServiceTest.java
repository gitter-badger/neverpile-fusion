package com.neverpile.fusion.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neverpile.fusion.api.CollectionIdStrategy;
import com.neverpile.fusion.api.CollectionService;
import com.neverpile.fusion.api.CollectionTypeService;
import com.neverpile.fusion.model.Collection;
import com.neverpile.fusion.model.CollectionType;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "server.error.include-message=always")
public class CollectionServiceTest extends AbstractRestAssuredTest {
  private static final String F = "aCollection";

  @TestConfiguration
  @Import(CollectionResource.class)
  public static class ServiceConfig {

  }

  @MockBean
  CollectionService mockCollectionService;
  
  @MockBean
  CollectionTypeService mockCollectionTypeService;

  @MockBean
  CollectionIdStrategy idGenerationStrategy;

  @Autowired
  ObjectMapper objectMapper;

  @BeforeEach
  public void reset() {
    AtomicInteger docIdGenerator = new AtomicInteger(42);
    BDDMockito.when(idGenerationStrategy.creatcollectionId()).thenAnswer(
        (i) -> "TheAnswerIs" + docIdGenerator.getAndIncrement());
    BDDMockito.when(idGenerationStrategy.validateCollectionId(any())).thenReturn(true);
    BDDMockito.when(mockCollectionTypeService.get(any())).thenReturn(Optional.of(new CollectionType()));
  }

  @Test
  public void testThat_collectionCanBeCreatedWithGeneratedId() throws Exception {
    // @formatter:off
    ArgumentCaptor<Collection> storedCollectionC = ArgumentCaptor.forClass(Collection.class);

    BDDMockito
      .given(mockCollectionService.save(storedCollectionC.capture()))
        .willAnswer(i -> { 
          Collection collection = i.getArgument(0);
          collection.setVersionTimestamp(Instant.now());
          collection.setDateCreated(Instant.now());
          collection.setDateModified(Instant.now());
          return collection; 
        });
    
    Instant then = Instant.now();
    
    // store collection
    Collection resCollection = RestAssured.given()
      .accept(ContentType.JSON)
      .body(createTestCollection()).contentType(ContentType.JSON)
      .auth().preemptive().basic("user", "password")
    .when()
      .log().all()
      .post("/api/v1/collections")
    .then()
      .log().all()
      .statusCode(201)
      .header("Location", Matchers.equalTo("TheAnswerIs42"))
      .contentType(ContentType.JSON)
      .extract().as(Collection.class);

    assertThat(resCollection.getId()).isEqualTo("TheAnswerIs42");
    assertThat(resCollection.getVersionTimestamp()).isBetween(then, Instant.now());

    // store another one
    Collection res2Collection = RestAssured.given()
      .accept(ContentType.JSON)
      .body(createTestCollection()).contentType(ContentType.JSON)
      .auth().preemptive().basic("user", "password")
    .when()
      .post("/api/v1/collections")
    .then()
      .statusCode(201)
      .contentType(ContentType.JSON)
      .extract().as(Collection.class);

    assertThat(res2Collection.getId()).isEqualTo("TheAnswerIs43");
    // @formatter:on
  }
  
  @Test
  public void testThat_collectionCanBeCreatedWithProvidedId() throws Exception {
    // @formatter:off
    ArgumentCaptor<Collection> storedCollectionC = ArgumentCaptor.forClass(Collection.class);
    
    BDDMockito
    .given(mockCollectionService.save(storedCollectionC.capture()))
    .willAnswer(i -> { 
      Collection collection = i.getArgument(0);
      collection.setVersionTimestamp(Instant.now());
      collection.setDateCreated(Instant.now());
      collection.setDateModified(Instant.now());
      return collection; 
    });
    
    Instant then = Instant.now();
    
    // store collection
    Collection resCollection = RestAssured.given()
        .accept(ContentType.JSON)
        .body(createTestCollection()).contentType(ContentType.JSON)
        .auth().preemptive().basic("user", "password")
      .when()
        .log().all()
        .put("/api/v1/collections/{id}", "iAmAProvidedId")
      .then()
        .log().all()
        .statusCode(201)
        .contentType(ContentType.JSON)
        .extract().as(Collection.class);
    
    assertThat(resCollection.getId()).isEqualTo("iAmAProvidedId");
    assertThat(resCollection.getVersionTimestamp()).isBetween(then, Instant.now());
    // @formatter:on
  }
  

  @Test
  public void testThat_collectionCreationValidatesCollectionType() throws Exception {
    // @formatter:off
    ArgumentCaptor<Collection> storedCollectionC = ArgumentCaptor.forClass(Collection.class);
    
    BDDMockito
    .given(mockCollectionService.save(storedCollectionC.capture()))
    .willAnswer(i -> { 
      Collection collection = i.getArgument(0);
      collection.setVersionTimestamp(Instant.now());
      collection.setDateCreated(Instant.now());
      collection.setDateModified(Instant.now());
      return collection; 
    });

    // store collection with null type id
    Collection c = createTestCollection();
    c.setTypeId(null);
    RestAssured.given()
        .accept(ContentType.JSON)
        .body(c).contentType(ContentType.JSON)
        .auth().preemptive().basic("user", "password")
      .when()
        .log().all()
        .post("/api/v1/collections")
      .then()
        .log().all()
        .statusCode(HttpStatus.NOT_ACCEPTABLE.value())
        .body("message", containsString("Type id is missing"));
    
    // find no type ids
    BDDMockito.when(mockCollectionTypeService.get(any())).thenReturn(Optional.empty());
    
    // store collection with nonexisting type id
    c.setTypeId("iDontExist");
    RestAssured.given()
        .accept(ContentType.JSON)
        .body(c).contentType(ContentType.JSON)
        .auth().preemptive().basic("user", "password")
      .when()
        .log().all()
        .post("/api/v1/collections")
      .then()
        .log().all()
        .statusCode(HttpStatus.NOT_ACCEPTABLE.value())
        .body("message", containsString("No such collection type"));
    
    // @formatter:on
  }
  
  @Test
  public void testThat_collectionCanBeUpdated() throws Exception {
    // @formatter:off
    ArgumentCaptor<Collection> storedCollectionC = ArgumentCaptor.forClass(Collection.class);
    
    BDDMockito
      .given(mockCollectionService.getCurrent(F))
      .willAnswer((a) -> { 
        Collection f = createTestCollection();
        f.setId(F);
        return Optional.of(f);
      });
    BDDMockito
      .given(mockCollectionService.save(storedCollectionC.capture()))
      .willAnswer(i -> { 
        Collection collection = i.getArgument(0);
        collection.setVersionTimestamp(Instant.now());
        return collection; 
      });
    
    Instant then = Instant.now();
    
    // store collection
    Collection resCollection = RestAssured.given()
        .accept(ContentType.JSON)
        .body(createTestCollection()).contentType(ContentType.JSON)
        .auth().preemptive().basic("user", "password")
      .when()
        .log().all()
        .put("/api/v1/collections/{id}", F)
      .then()
        .statusCode(201)
        .contentType(ContentType.JSON)
        .extract().as(Collection.class);
    
    assertThat(resCollection.getId()).isEqualTo(F);
    assertThat(resCollection.getVersionTimestamp()).isBetween(then, Instant.now());
    // @formatter:on
  }

  private Collection createTestCollection() throws JsonProcessingException {
    Collection collection = new Collection();

    collection.setTypeId("foo");
    collection.setMetadata(objectMapper.createObjectNode().put("foo", "bar"));

    return collection;
  }

  @Test
  public void testThat_collectionCanBeRetrievedAsJSON() throws Exception {
    // @formatter:off
    // retrieve it
    BDDMockito
      .given(mockCollectionService.getCurrent(F))
      .willAnswer((a) -> { 
        Collection f = createTestCollection();
        f.setId(F);
        return Optional.of(f);
      });

    Collection returnedDoss = RestAssured
      .given()
        .accept(ContentType.JSON)
        .body(createTestCollection()).contentType(ContentType.JSON)
        .auth().preemptive().basic("user", "password")
      .when()
        .get("/api/v1/collections/{id}", F)
      .then()
        .log().all()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("id", equalTo(F))
        .extract().as(Collection.class);
    // @formatter:on

    // verify returned document
    assertThat(returnedDoss.getId()).isEqualTo(F);
  }
  
  @Test
  public void testThat_collectionVersionCanBeRetrievedAsJSON() throws Exception {
    // @formatter:off
    Instant then = Instant.now();
    
    // retrieve it
    BDDMockito
      .given(mockCollectionService.getVersion(F, then))
        .willAnswer((a) -> { 
          Collection f = createTestCollection();
          f.setId(F);
          return Optional.of(f);
        });
    
    Collection returnedDoss = RestAssured
      .given()
        .accept(ContentType.JSON)
        .body(createTestCollection()).contentType(ContentType.JSON)
        .auth().preemptive().basic("user", "password")
      .when()
        .log().all()
        .get("/api/v1/collections/{id}/history/{then}", F, then.toString())
      .then()
        .log().all()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("id", equalTo(F))
        .extract().as(Collection.class);
    // @formatter:on
    
    // verify returned document
    assertThat(returnedDoss.getId()).isEqualTo(F);
  }
}
