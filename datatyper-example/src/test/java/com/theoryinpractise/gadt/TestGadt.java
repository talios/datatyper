package com.theoryinpractise.gadt;

import com.theoryinpractise.gadt.examples.Customer;
import com.theoryinpractise.gadt.examples.GenericType;
import com.theoryinpractise.gadt.examples.Request;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.truth.Truth.assertThat;

/** Test class is in a different package to generated files to check visibility constraints. */
public class TestGadt {
  @Test
  public void testUsingGadt() {
    Request req = Request.GET("/api/story/32");

    assertThat(req.toString()).startsWith("GET{path=/api/story/32");
    assertThat(req).isInstanceOf(Request.class);
    assertThat(req).isInstanceOf(Request.GET.class);

    // Test for total matching

    int pathLength =
        req.match(
            new Request.Matcher<Integer>() {
              @Override
              public Integer GET(Request.GET GET) {
                return GET.path().length();
              }

              @Override
              public Integer DELETE(Request.DELETE DELETE) {
                return DELETE.path().length();
              }

              @Override
              public Integer POST(Request.POST POST) {
                return POST.path().length();
              }
            });

    assertThat(pathLength).isEqualTo(13);

    // Test for total matching via lambdas

    pathLength =
        req.matching(
            GET -> GET.path().length(),
            DELETE -> DELETE.path().length(),
            POST -> POST.path().length());

    assertThat(pathLength).isEqualTo(13);

    // Test for fluent matching
    Request.Matching<String> matching = req.<String>matching().GET(get -> "Hello");
    if (matching.isMatched()) {
      assertThat(matching.get()).isEqualTo("Hello");
    } else {
      throw new AssertionError("This should have been matched.");
    }

    // Test for failed matching
    try {
      matching = req.<String>matching().DELETE(get -> "Hello");
      assertThat(matching.get()).isEqualTo("Hello");
      throw new AssertionError("This should throw.");
    } catch (IllegalStateException e) {
      // expected
    }

    // Test for total matching
    String returnValue = req.<String>matching().POST(post -> "Hello").orElse("Goodbye");

    assertThat(returnValue).isEqualTo("Goodbye");

    // Test for optional matching
    Optional<String> optionalReturnValue = req.<String>matching().POST(post -> "Hello").find();

    assertThat(optionalReturnValue.isPresent()).isFalse();

    req.shout();
  }

  @Test
  public void testUsingBasicSingleton() {
    Customer.SimpleCustomer cust = Customer.simpleCustomer("Test", "Customer");
    assertThat(cust.firstName()).isEqualTo("Test");
  }

  @Test
  public void testGenericType() {
    GenericType<String> genType = GenericType.single("Hello");

    genType.shout();

    int length =
        genType.matching(
            empty -> 0, single -> single.value().length(), container -> container.value().size());

    assertThat(length).isEqualTo(5);
  }

  @Test
  public void testAcceptingBasicSingleton() {
    Request req = Request.GET("/api/story/32");

    AtomicBoolean done = new AtomicBoolean(false);

    req.accepting().POST(r -> done.set(false)).orElse(r -> done.set(true));

    assertThat(done.get()).isTrue();
  }
}
