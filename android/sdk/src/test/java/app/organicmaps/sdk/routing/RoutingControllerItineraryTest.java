package app.organicmaps.sdk.routing;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import app.organicmaps.sdk.Router;
import app.organicmaps.sdk.bookmarks.data.MapObject;
import app.organicmaps.sdk.util.log.Logger;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.mockito.MockedStatic;

public class RoutingControllerItineraryTest
{
  private static RouteMarkData point(String title, RouteMarkType type, int index, double coordinate)
  {
    return new RouteMarkData(title, "subtitle:" + title, type, index, true, false, false, coordinate, 1);
  }

  private static final class Controller extends RoutingController
  {
    RouteMarkData[] points = {point("A", RouteMarkType.Start, 0, 1), point("B", RouteMarkType.Intermediate, 0, 2),
                              point("C", RouteMarkType.Intermediate, 1, 3), point("D", RouteMarkType.Finish, 0, 4)};
    final List<String> events = new ArrayList<>();
    int builds;

    Controller() throws ReflectiveOperationException
    {
      field("mLastRouterType").set(this, Router.Bicycle);
    }

    @Override
    public RouteMarkData[] getRoutePoints()
    {
      return points.clone();
    }

    @Override
    void build()
    {
      ++builds;
      events.add("build");
    }

    @Override
    public void prepare(MapObject start, MapObject finish)
    {
      fail("A rebuild must use the existing native itinerary");
    }

    void loaded() throws ReflectiveOperationException
    {
      ((RoutingLoadPointsListener) field("mRoutingLoadPointsListener").get(this)).onRoutePointsLoaded(true);
    }
  }

  private static Field field(String name) throws ReflectiveOperationException
  {
    final Field field = RoutingController.class.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  @Test
  public void rebuildRequestsCalculationWithAllStopsAndSelectedRouter() throws ReflectiveOperationException
  {
    final Controller controller = new Controller();
    final RouteMarkData[] before = controller.getRoutePoints();
    controller.attach(new RoutingController.Container() {
      @Override
      public void showRoutePlan(boolean show)
      {
        assertTrue(show);
        controller.events.add("plan");
      }

      @Override
      public void onPlanningStarted()
      {
        controller.events.add("started");
      }
    });
    try (MockedStatic<Logger> ignored = mockStatic(Logger.class))
    {
      controller.rebuildLastRoute();
    }
    assertTrue(controller.isPlanning());
    assertEquals(List.of("plan", "build", "started"), controller.events);
    assertEquals(1, controller.builds);
    assertEquals(Router.Bicycle, controller.getLastRouterType());
    assertArrayEquals(before, controller.getRoutePoints());
  }

  @Test
  public void detachedRestorationBuildsOnceAndReattachmentOnlyRenders() throws ReflectiveOperationException
  {
    final Controller controller = new Controller();
    controller.attach(mock(RoutingController.Container.class));
    controller.onSaveState();
    controller.detach();
    try (MockedStatic<Logger> ignored = mockStatic(Logger.class))
    {
      controller.loaded();
      assertEquals(1, controller.builds);
      assertTrue(controller.isPlanning());
      controller.attach(mock(RoutingController.Container.class));
      controller.restore();
      controller.restore();
    }
    assertEquals(1, controller.builds);
    assertEquals(Router.Bicycle, controller.getLastRouterType());
    assertEquals(4, controller.getRoutePoints().length);
  }

  @Test
  public void rebuildingAPartialPlanDoesNotRequestCalculation() throws ReflectiveOperationException
  {
    final Controller controller = new Controller();
    controller.points = new RouteMarkData[] {point("D", RouteMarkType.Finish, 0, 4)};
    try (MockedStatic<Logger> ignored = mockStatic(Logger.class))
    {
      controller.rebuildLastRoute();
    }
    assertTrue(controller.isPlanning());
    assertEquals(0, controller.builds);
  }

  @Test
  public void rebuildDiscardsAnErrorCachedBeforeReattachment() throws ReflectiveOperationException
  {
    final Controller controller = new Controller();
    final RoutingController.Container container = mock(RoutingController.Container.class);
    field("mContainsCachedResult").setBoolean(controller, true);
    field("mLastResultCode").setInt(controller, ResultCodes.ROUTE_NOT_FOUND);
    field("mLastMissingMaps").set(controller, new String[0]);
    try (MockedStatic<Logger> ignored = mockStatic(Logger.class);
         MockedStatic<RoutingOptions> options = mockStatic(RoutingOptions.class))
    {
      controller.loaded();
      controller.attach(container);
      controller.restore();
    }
    verify(container, never()).onCommonBuildError(anyInt(), any());
    assertFalse(controller.isErrorEncountered());
  }

  @Test
  public void aNewerItineraryRejectsTheQueuedMoveWithoutBuilding() throws ReflectiveOperationException
  {
    final Controller controller = new Controller();
    final List<RouteMarkData> expected = List.of(controller.getRoutePoints());
    // JNI creates fresh objects on every read; unchanged point contents must still match.
    assertEquals(expected, List.of(new Controller().getRoutePoints()));
    try (MockedStatic<Logger> ignored = mockStatic(Logger.class))
    {
      controller.rebuildLastRoute();
      // Reverse keeps the same number of marks but changes the meaning of every index.
      controller.points =
          new RouteMarkData[] {point("D", RouteMarkType.Start, 0, 4), point("C", RouteMarkType.Intermediate, 0, 3),
                               point("B", RouteMarkType.Intermediate, 1, 2), point("A", RouteMarkType.Finish, 0, 1)};
      assertFalse(controller.moveRoutePoint(expected, 0, 2));
      // Replacing a point at the same coordinates is also a newer snapshot.
      controller.points = expected.toArray(new RouteMarkData[0]);
      controller.points[1] = point("new stop", RouteMarkType.Intermediate, 0, 2);
      assertFalse(controller.moveRoutePoint(expected, 0, 2));
    }
    assertEquals(1, controller.builds);
  }

  @Test
  public void cancelledUnchangedAndInvalidMovesDoNotReachNative() throws ReflectiveOperationException
  {
    final Controller controller = new Controller();
    final List<RouteMarkData> expected = List.of(controller.getRoutePoints());
    assertFalse(controller.moveRoutePoint(expected, 0, 2));
    try (MockedStatic<Logger> ignored = mockStatic(Logger.class))
    {
      controller.rebuildLastRoute();
      assertFalse(controller.moveRoutePoint(expected, 1, 1));
      assertFalse(controller.moveRoutePoint(expected, -1, 2));
      assertFalse(controller.moveRoutePoint(expected, 0, expected.size()));
    }
    assertEquals(1, controller.builds);
  }
}
