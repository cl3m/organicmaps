package app.organicmaps.sdk.routing;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import java.util.Objects;

/**
 * Immutable snapshot of a route point from core.
 */
// Called from JNI.
@Keep
@SuppressWarnings("unused")
public final class RouteMarkData
{
  @Nullable
  public final String mTitle;
  @Nullable
  public final String mSubtitle;
  public final RouteMarkType mPointType;
  public final int mIntermediateIndex;
  public final boolean mIsVisible;
  public final boolean mIsMyPosition;
  public final boolean mIsPassed;
  public final double mLat;
  public final double mLon;

  private RouteMarkData(@Nullable String title, @Nullable String subtitle, int pointType, int intermediateIndex,
                        boolean isVisible, boolean isMyPosition, boolean isPassed, double lat, double lon)
  {
    this(title, subtitle, RouteMarkType.values()[pointType], intermediateIndex, isVisible, isMyPosition, isPassed, lat,
         lon);
  }

  public RouteMarkData(@Nullable String title, @Nullable String subtitle, RouteMarkType pointType,
                       int intermediateIndex, boolean isVisible, boolean isMyPosition, boolean isPassed, double lat,
                       double lon)
  {
    mTitle = title;
    mSubtitle = subtitle;
    mPointType = pointType;
    mIntermediateIndex = intermediateIndex;
    mIsVisible = isVisible;
    mIsMyPosition = isMyPosition;
    mIsPassed = isPassed;
    mLat = lat;
    mLon = lon;
  }

  @Override
  public boolean equals(Object other)
  {
    if (!(other instanceof RouteMarkData point))
      return false;
    if (mPointType != point.mPointType || mIntermediateIndex != point.mIntermediateIndex
        || mIsVisible != point.mIsVisible || mIsMyPosition != point.mIsMyPosition || mIsPassed != point.mIsPassed
        || Double.compare(mLat, point.mLat) != 0 || Double.compare(mLon, point.mLon) != 0)
      return false;
    return Objects.equals(mTitle, point.mTitle) && Objects.equals(mSubtitle, point.mSubtitle);
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(mTitle, mSubtitle, mPointType, mIntermediateIndex, mIsVisible, mIsMyPosition, mIsPassed, mLat,
                        mLon);
  }
}
