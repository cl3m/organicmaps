#include "testing/testing.hpp"

#include "routing/router.hpp"

#include <optional>
#include <string_view>

namespace router_tests
{
UNIT_TEST(RouterType_Parsing)
{
  for (int i = 0; i < static_cast<int>(routing::RouterType::Count); ++i)
  {
    auto const type = static_cast<routing::RouterType>(i);
    auto const name = routing::ToString(type);
    TEST_EQUAL(routing::FromStringChecked(name), std::optional(type), (name));
    TEST_EQUAL(routing::FromString(name), type, (name));
  }

  for (std::string_view const name : {"", "count", "future-router", "Vehicle", "pedestrian "})
    TEST(!routing::FromStringChecked(name), (name));
}
}  // namespace router_tests
