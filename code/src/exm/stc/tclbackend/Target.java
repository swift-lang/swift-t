package exm.stc.tclbackend;

import exm.stc.tclbackend.tree.Token;

/**
 * The target of a Turbine rule 
 * Becomes the target of the ADLB task
 * The target is either ADLB_RANK_ANY or an non-negative integer rank
 * @author wozniak
 * */
public class Target
{
  boolean rankAny = false;
  int targetRank = -1;
  
  static final Token ADLB_RANK_ANY = new Token("$adlb::RANK_ANY");
  
  Target(boolean rankAny, int targetRank)
  {
    this.rankAny = rankAny;
    this.targetRank = targetRank;
  }
  
  /**
     Constructor: Targets task to any rank
   */
  static Target rankAny()
  {
    return new Target(true, -1);
  }
  
  /**
     Constructor: Targets task to specific target rank
   */
  static Target rank(int targetRank)
  {
    return new Target(false, targetRank);
  }
  
  Token toTcl()
  {
    if (rankAny)
      return ADLB_RANK_ANY;
    else
      return new Token(targetRank);
  }
}
