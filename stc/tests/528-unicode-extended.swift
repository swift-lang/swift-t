// SKIP-THIS-TEST
// Support for unicode characters outside basic multilingual plane
// E.g. ones that aren't representable in UCS-16
import string;

main {
  // Hieroglyphics - supplemental multilingual plan
  // Insert some operations to check they behave ok
  trace("𓀀𓅸 " + sprintf("𓉀%s", " 𓐮"));

  //  CJK Unified Ideographs Extension B - supplemental ideographic plane
  trace("𠀀 𠀁 𠀂 𠀃 𠀄 𠀅 𠀆 𠀇 𠀈 𠀉 𠀊 𠀋");
}

