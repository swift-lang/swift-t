package exm.stc.frontend;

/**
 * Control what statement walker should process
 */
enum WalkMode {
  NORMAL,
  ONLY_DECLARATIONS, // Only process variable declarations
  ONLY_EVALUATION, // Process everything but declarations 
}