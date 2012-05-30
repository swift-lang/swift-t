package exm.stc.ast.descriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import exm.stc.ast.antlr.ExMParser;
import exm.stc.ast.SwiftAST;
import exm.stc.frontend.Context;

public class ArrayElems {
  
  private final ArrayList<SwiftAST> members;
  
  
  public List<SwiftAST> getMembers() {
    return Collections.unmodifiableList(members);
  }
  
  public int getMemberCount() {
    return members.size();
  }

  public SwiftAST getMember(int i) {
    return members.get(i);
  }
  
  public ArrayElems(ArrayList<SwiftAST> members) {
    super();
    this.members = members;
  }
  
  public static ArrayElems fromAST(Context context, SwiftAST tree) {
    assert(tree.getType() == ExMParser.ARRAY_ELEMS);
    ArrayList<SwiftAST> members = new ArrayList<SwiftAST>(tree.getChildCount());
    for (int i = 0; i < tree.getChildCount(); i++) {
      members.add(tree.child(i));
    }
    return new ArrayElems(members);
  }
}
