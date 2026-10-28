package app.organicmaps.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.view.View;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.sdk.routing.RouteMarkData;
import app.organicmaps.sdk.routing.RouteMarkType;
import app.organicmaps.sdk.routing.RoutingController;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.mockito.MockedStatic;

public class ManageRouteControllerTest
{
  private static RouteMarkData point(RouteMarkType type, int index)
  {
    return new RouteMarkData(type.name(), "", type, index, true, false, false, index, index);
  }

  private static ManageRouteController controller(RouteMarkData... points) throws ReflectiveOperationException
  {
    final ManageRouteController controller = mock(ManageRouteController.class, CALLS_REAL_METHODS);
    final Field adapter = ManageRouteController.class.getDeclaredField("mManageRouteAdapter");
    adapter.setAccessible(true);
    adapter.set(controller, new ManageRouteAdapter(mock(Context.class), points,
                                                   mock(ManageRouteAdapter.ManageRouteListener.class)));
    return controller;
  }

  private static RecyclerView.ViewHolder holder(int position)
  {
    final RecyclerView.ViewHolder holder = mock(RecyclerView.ViewHolder.class);
    when(holder.getBindingAdapterPosition()).thenReturn(position);
    return holder;
  }

  private static final class DragFixture
  {
    final ManageRouteAdapter adapter;
    final ManageRouteController controller;
    final RoutingController routing = mock(RoutingController.class);
    final RecyclerView recycler = mock(RecyclerView.class);
    final List<Runnable> posted = new ArrayList<>();
    final ItemTouchHelper.Callback drag;
    final List<RouteMarkData> startOrder;

    DragFixture() throws ReflectiveOperationException
    {
      this(point(RouteMarkType.Start, 0), point(RouteMarkType.Intermediate, 0), point(RouteMarkType.Finish, 0));
    }

    DragFixture(RouteMarkData... points) throws ReflectiveOperationException
    {
      controller = controller(points);
      final Field field = ManageRouteController.class.getDeclaredField("mManageRouteAdapter");
      field.setAccessible(true);
      adapter = spy((ManageRouteAdapter) field.get(controller));
      field.set(controller, adapter);
      startOrder = adapter.getRoutePoints();
      doNothing().when(adapter).notifyItemMoved(anyInt(), anyInt());
      doNothing().when(adapter).notifyDataSetChanged();
      doNothing().when(controller).refresh();
      when(routing.moveRoutePoint(any(), anyInt(), anyInt())).thenReturn(true);
      final Class<?> callback =
          Class.forName(ManageRouteController.class.getName() + "$ManageRouteItemTouchHelperCallback");
      final Constructor<?> constructor =
          callback.getDeclaredConstructor(ManageRouteAdapter.class, ManageRouteController.class);
      constructor.setAccessible(true);
      drag = (ItemTouchHelper.Callback) constructor.newInstance(adapter, controller);
      when(routing.isPlanning()).thenReturn(true);
      when(recycler.isAttachedToWindow()).thenReturn(true);
      when(recycler.post(any())).thenAnswer(call -> {
        posted.add(call.getArgument(0));
        return true;
      });
    }

    RecyclerView.ViewHolder pointHolder(int position)
    {
      final ManageRouteAdapter.ManageRouteViewHolder holder =
          spy(new ManageRouteAdapter.ManageRouteViewHolder(mock(View.class)));
      doReturn(position).when(holder).getBindingAdapterPosition();
      return holder;
    }

    RecyclerView.ViewHolder move()
    {
      final RecyclerView.ViewHolder source = pointHolder(0);
      drag.onSelectedChanged(source, ItemTouchHelper.ACTION_STATE_DRAG);
      assertTrue(drag.onMove(recycler, source, pointHolder(1)));
      doReturn(1).when(source).getBindingAdapterPosition();
      return source;
    }
  }

  @Test
  public void editsStayBlockedThroughDragAndRecovery() throws ReflectiveOperationException
  {
    final DragFixture fixture = new DragFixture();
    try (MockedStatic<RoutingController> singleton = mockStatic(RoutingController.class))
    {
      singleton.when(RoutingController::get).thenReturn(fixture.routing);
      final RecyclerView.ViewHolder source = fixture.move();
      fixture.controller.onRoutePointDeleted(fixture.pointHolder(0));
      fixture.drag.clearView(fixture.recycler, source);
      fixture.controller.onRoutePointDeleted(fixture.pointHolder(0));
      verify(fixture.routing, never()).removeStop(any(), anyInt());
      fixture.posted.get(0).run();
      verify(fixture.routing).moveRoutePoint(fixture.startOrder, 0, 1);
      fixture.controller.onRoutePointDeleted(fixture.pointHolder(2));
      verify(fixture.routing).removeStop(RouteMarkType.Finish, 0);
    }
  }

  @Test
  public void refreshDoesNotReplaceThePendingOrder() throws ReflectiveOperationException
  {
    final DragFixture fixture = new DragFixture();
    fixture.move();
    doCallRealMethod().when(fixture.controller).refresh();
    fixture.controller.refresh();
    assertEquals(RouteMarkType.Intermediate.name(), fixture.adapter.getRoutePoints().get(0).mTitle);
  }

  @Test
  public void cancelledOrDestroyedPlannerDiscardsItsQueuedDrop() throws ReflectiveOperationException
  {
    for (boolean attached : new boolean[] {true, false})
    {
      final DragFixture fixture = new DragFixture();
      try (MockedStatic<RoutingController> singleton = mockStatic(RoutingController.class))
      {
        singleton.when(RoutingController::get).thenReturn(fixture.routing);
        fixture.drag.clearView(fixture.recycler, fixture.move());
        when(fixture.recycler.isAttachedToWindow()).thenReturn(attached);
        when(fixture.routing.isPlanning()).thenReturn(!attached);
        fixture.posted.get(0).run();
        verify(fixture.routing, never()).moveRoutePoint(any(), anyInt(), anyInt());
        verify(fixture.controller, never()).refresh();
      }
    }
  }

  @Test
  public void regrabbingRecoveryLetsOnlyTheLatestDropCommit() throws ReflectiveOperationException
  {
    final DragFixture fixture = new DragFixture();
    try (MockedStatic<RoutingController> singleton = mockStatic(RoutingController.class))
    {
      singleton.when(RoutingController::get).thenReturn(fixture.routing);
      final RecyclerView.ViewHolder source = fixture.move();
      fixture.drag.clearView(fixture.recycler, source);
      fixture.drag.onSelectedChanged(source, ItemTouchHelper.ACTION_STATE_DRAG);
      fixture.posted.get(0).run();
      verify(fixture.routing, never()).moveRoutePoint(any(), anyInt(), anyInt());
      fixture.controller.onRoutePointDeleted(fixture.pointHolder(0));
      verify(fixture.routing, never()).removeStop(any(), anyInt());
      fixture.drag.clearView(fixture.recycler, source);
      fixture.posted.get(1).run();
      verify(fixture.routing).moveRoutePoint(fixture.startOrder, 0, 1);
    }
  }

  @Test
  public void unchangedDropRefreshesWithoutRebuilding() throws ReflectiveOperationException
  {
    final DragFixture fixture = new DragFixture();
    try (MockedStatic<RoutingController> singleton = mockStatic(RoutingController.class))
    {
      singleton.when(RoutingController::get).thenReturn(fixture.routing);
      final RecyclerView.ViewHolder source = fixture.pointHolder(0);
      fixture.drag.onSelectedChanged(source, ItemTouchHelper.ACTION_STATE_DRAG);
      fixture.drag.clearView(fixture.recycler, source);
      fixture.posted.get(0).run();
      verify(fixture.routing, never()).moveRoutePoint(any(), anyInt(), anyInt());
      verify(fixture.controller).refresh();
    }
  }

  @Test
  public void crossingSeveralRowsCommitsOneMoveFromTheOriginalIndex() throws ReflectiveOperationException
  {
    final DragFixture fixture = new DragFixture();
    try (MockedStatic<RoutingController> singleton = mockStatic(RoutingController.class))
    {
      singleton.when(RoutingController::get).thenReturn(fixture.routing);
      final RecyclerView.ViewHolder source = fixture.move();
      fixture.drag.onMove(fixture.recycler, source, fixture.pointHolder(2));
      doReturn(2).when(source).getBindingAdapterPosition();
      fixture.drag.clearView(fixture.recycler, source);
      fixture.posted.get(0).run();
      verify(fixture.routing, times(1)).moveRoutePoint(fixture.startOrder, 0, 2);
      assertEquals(RouteMarkType.Start, fixture.startOrder.get(0).mPointType);
      assertEquals(RouteMarkType.Intermediate, fixture.startOrder.get(1).mPointType);
    }
  }

  @Test
  public void draggingTheFinishToTheStartCommitsTheBackwardMove() throws ReflectiveOperationException
  {
    final DragFixture fixture = new DragFixture();
    try (MockedStatic<RoutingController> singleton = mockStatic(RoutingController.class))
    {
      singleton.when(RoutingController::get).thenReturn(fixture.routing);
      final RecyclerView.ViewHolder source = fixture.pointHolder(2);
      fixture.drag.onSelectedChanged(source, ItemTouchHelper.ACTION_STATE_DRAG);
      fixture.drag.onMove(fixture.recycler, source, fixture.pointHolder(0));
      doReturn(0).when(source).getBindingAdapterPosition();
      fixture.drag.clearView(fixture.recycler, source);
      fixture.posted.get(0).run();
      verify(fixture.routing).moveRoutePoint(fixture.startOrder, 2, 0);
    }
  }

  @Test
  public void regrabbingAndReturningToTheOriginalPositionDoesNotBuild() throws ReflectiveOperationException
  {
    final DragFixture fixture = new DragFixture();
    try (MockedStatic<RoutingController> singleton = mockStatic(RoutingController.class))
    {
      singleton.when(RoutingController::get).thenReturn(fixture.routing);
      final RecyclerView.ViewHolder source = fixture.move();
      fixture.drag.clearView(fixture.recycler, source);
      fixture.drag.onSelectedChanged(source, ItemTouchHelper.ACTION_STATE_DRAG);
      fixture.drag.onMove(fixture.recycler, source, fixture.pointHolder(0));
      fixture.drag.clearView(fixture.recycler, source);
      fixture.posted.get(0).run();
      fixture.posted.get(1).run();
      verify(fixture.routing, never()).moveRoutePoint(any(), anyInt(), anyInt());
      verify(fixture.controller).refresh();
    }
  }

  @Test
  public void regrabbingAnotherRowCommitsTheMoveShownByTheFinalOrder() throws ReflectiveOperationException
  {
    final DragFixture fixture = new DragFixture();
    try (MockedStatic<RoutingController> singleton = mockStatic(RoutingController.class))
    {
      singleton.when(RoutingController::get).thenReturn(fixture.routing);
      // A-B-C -> B-A-C, then grab B and move it to the end: A-C-B.
      fixture.drag.clearView(fixture.recycler, fixture.move());
      final RecyclerView.ViewHolder second = fixture.pointHolder(0);
      fixture.drag.onSelectedChanged(second, ItemTouchHelper.ACTION_STATE_DRAG);
      fixture.drag.onMove(fixture.recycler, second, fixture.pointHolder(2));
      doReturn(2).when(second).getBindingAdapterPosition();
      fixture.drag.clearView(fixture.recycler, second);
      fixture.posted.get(0).run();
      verify(fixture.routing, never()).moveRoutePoint(any(), anyInt(), anyInt());
      fixture.posted.get(1).run();
      assertEquals(List.of(fixture.startOrder.get(0), fixture.startOrder.get(2), fixture.startOrder.get(1)),
                   fixture.adapter.getRoutePoints());
      verify(fixture.routing).moveRoutePoint(fixture.startOrder, 1, 2);
    }
  }

  @Test
  public void olderRowsRecoveryCannotCommitTheNewRowsDrag() throws ReflectiveOperationException
  {
    final DragFixture fixture = new DragFixture();
    try (MockedStatic<RoutingController> singleton = mockStatic(RoutingController.class))
    {
      singleton.when(RoutingController::get).thenReturn(fixture.routing);
      final RecyclerView.ViewHolder first = fixture.move();
      fixture.drag.onSelectedChanged(null, ItemTouchHelper.ACTION_STATE_IDLE);
      final RecyclerView.ViewHolder second = fixture.pointHolder(0);
      fixture.drag.onSelectedChanged(second, ItemTouchHelper.ACTION_STATE_DRAG);
      fixture.drag.clearView(fixture.recycler, first);
      assertTrue(fixture.posted.isEmpty());
      fixture.controller.onRoutePointDeleted(fixture.pointHolder(2));
      verify(fixture.routing, never()).removeStop(any(), anyInt());
      fixture.drag.onMove(fixture.recycler, second, fixture.pointHolder(2));
      doReturn(2).when(second).getBindingAdapterPosition();
      fixture.drag.clearView(fixture.recycler, second);
      fixture.posted.get(0).run();
      verify(fixture.routing).moveRoutePoint(fixture.startOrder, 1, 2);
    }
  }

  @Test
  public void regrabbingAnotherRowDiscardsAnOrderThatNeedsMultipleMoves() throws ReflectiveOperationException
  {
    final DragFixture fixture = new DragFixture(point(RouteMarkType.Start, 0), point(RouteMarkType.Intermediate, 0),
                                                point(RouteMarkType.Intermediate, 1), point(RouteMarkType.Finish, 0));
    try (MockedStatic<RoutingController> singleton = mockStatic(RoutingController.class))
    {
      singleton.when(RoutingController::get).thenReturn(fixture.routing);
      // Two independent swaps cannot be represented by one native move: A-B-C-D -> B-A-D-C.
      fixture.drag.clearView(fixture.recycler, fixture.move());
      final RecyclerView.ViewHolder second = fixture.pointHolder(3);
      fixture.drag.onSelectedChanged(second, ItemTouchHelper.ACTION_STATE_DRAG);
      fixture.drag.onMove(fixture.recycler, second, fixture.pointHolder(2));
      doReturn(2).when(second).getBindingAdapterPosition();
      fixture.drag.clearView(fixture.recycler, second);
      fixture.posted.get(0).run();
      fixture.posted.get(1).run();
      verify(fixture.routing, never()).moveRoutePoint(any(), anyInt(), anyInt());
      verify(fixture.controller).refresh();
    }
  }

  @Test
  public void rejectedDropRefreshesTheNewerNativeItinerary() throws ReflectiveOperationException
  {
    final DragFixture fixture = new DragFixture();
    final RouteMarkData[] latest = {point(RouteMarkType.Start, 10), point(RouteMarkType.Finish, 20)};
    when(fixture.routing.moveRoutePoint(eq(fixture.startOrder), anyInt(), anyInt())).thenReturn(false);
    when(fixture.routing.getRoutePoints()).thenReturn(latest);
    doCallRealMethod().when(fixture.controller).refresh();
    try (MockedStatic<RoutingController> singleton = mockStatic(RoutingController.class))
    {
      singleton.when(RoutingController::get).thenReturn(fixture.routing);
      fixture.drag.clearView(fixture.recycler, fixture.move());
      fixture.posted.get(0).run();
      verify(fixture.routing).moveRoutePoint(fixture.startOrder, 0, 1);
      assertEquals(List.of(latest), fixture.adapter.getRoutePoints());
    }
  }

  @Test
  public void invalidDeletePositionsDoNotReachTheRoutingController() throws ReflectiveOperationException
  {
    final RoutingController routing = mock(RoutingController.class);
    try (MockedStatic<RoutingController> singleton = mockStatic(RoutingController.class))
    {
      singleton.when(RoutingController::get).thenReturn(routing);
      controller(point(RouteMarkType.Start, 0)).onRoutePointDeleted(holder(RecyclerView.NO_POSITION));
      controller(point(RouteMarkType.Start, 0), point(RouteMarkType.Finish, 0))
          .onRoutePointDeleted(holder(RecyclerView.NO_POSITION));
      verifyNoInteractions(routing);
    }
  }

  @Test
  public void bothPartialSlotsDeleteTheirOnlyRealPoint() throws ReflectiveOperationException
  {
    final RoutingController routing = mock(RoutingController.class);
    try (MockedStatic<RoutingController> singleton = mockStatic(RoutingController.class))
    {
      singleton.when(RoutingController::get).thenReturn(routing);
      controller(point(RouteMarkType.Start, 0)).onRoutePointDeleted(holder(0));
      controller(point(RouteMarkType.Finish, 0)).onRoutePointDeleted(holder(1));
      verify(routing).removeStop(RouteMarkType.Start, 0);
      verify(routing).removeStop(RouteMarkType.Finish, 0);
    }
  }

  @Test
  public void completeRouteDeletionUsesThePointTypeAndIntermediateIndex() throws ReflectiveOperationException
  {
    final RoutingController routing = mock(RoutingController.class);
    final ManageRouteController controller =
        controller(point(RouteMarkType.Start, 0), point(RouteMarkType.Intermediate, 0),
                   point(RouteMarkType.Intermediate, 1), point(RouteMarkType.Finish, 0));
    try (MockedStatic<RoutingController> singleton = mockStatic(RoutingController.class))
    {
      singleton.when(RoutingController::get).thenReturn(routing);
      controller.onRoutePointDeleted(holder(2));
      verify(routing).removeStop(RouteMarkType.Intermediate, 1);
    }
  }
}
