#include "routing/router.hpp"

namespace routing
{
std::string ToString(RouterType type)
{
  switch (type)
  {
  case RouterType::Vehicle: return "vehicle";
  case RouterType::Pedestrian: return "pedestrian";
  case RouterType::Bicycle: return "bicycle";
  case RouterType::Transit: return "transit";
  case RouterType::Ruler: return "ruler";
  case RouterType::Count: return "count";
  }
  ASSERT(false, ());
  return "Error";
}

RouterType FromString(std::string const & str)
{
  auto const type = FromStringChecked(str);
  ASSERT(type.has_value(), ("Incorrect routing string:", str));
  return type.value_or(RouterType::Vehicle);
}

std::optional<RouterType> FromStringChecked(std::string_view str)
{
  if (str == "vehicle")
    return RouterType::Vehicle;
  if (str == "pedestrian")
    return RouterType::Pedestrian;
  if (str == "bicycle")
    return RouterType::Bicycle;
  if (str == "transit")
    return RouterType::Transit;
  if (str == "ruler")
    return RouterType::Ruler;
  return std::nullopt;
}

std::string DebugPrint(RouterType type)
{
  return ToString(type);
}
}  //  namespace routing
