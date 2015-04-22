#!/usr/bin/env python
"""
Tool to visualize state of ADLB data based on ADLB debug information
"""
import fileinput
import re
import pygraphviz as gv

class Datum:
  def __init__(self, id):
    self.id = id
    self.name = None
    self.type = None
    self.key_type = None
    self.val_type = None
    self.value = None
    self.references = []
    self.leaked = False
    self.freed = False
    self.read_rc = None
    self.write_rc = None
    self.in_edges = set()

  def set_value(self, value):
    self.value = value
    if self.value is not None and self.type is not None:
      self.references = refs_from_val(self.type, self.value)

  def add_subscript_value(self, subscript, value):
    sub_val = "%s=%s" % (subscript, value)
    if self.value is None:
      self.value = sub_val
    else:
      self.value += " " + sub_val
    
    # need container type info
    if self.val_type is not None:
      sub_refs = refs_from_val(self.val_type, value)
      for k, v in sub_refs:
        if k is None:
          self.references.append((subscript, v))
        else:
          self.references.append((subscript + "." + k, v))

  def add_in_edge(self, source):
    """
    Note that there is a in-edge from source Datum
    """
    self.in_edges.add(source)

  def __str__(self):
    return "%ld %s" % (self.id, self.name)

datums = {}

def get_datum(id_, create=True):
  datum = datums.get(id_, None)
  if datum is None:
    if not create:
      return None
    datum = Datum(id_)
    datums[id_] = datum
  return datum

ID_REGEX = re.compile("<(\d*)>")
FILE_REF_REGEX = re.compile("status:<(\d*)> filename:<(\d*)> mapped:(\d)")
STRUCT_ELEM = re.compile("{([^{}]*)}={([^{}]*)}")
STRUCT_START = re.compile("^[A-Za-z_]*: {.*")
CONTAINER_ELEM = re.compile("\"([^\"]*)\"={([^{}]*)}")
CONTAINER_START = re.compile("^[a-z_]*=>[a-z_]*: .*")

def refs_from_val(type, value):
  """
  extract list of ids
  """
  # Proceed based on type, or pattern matching
  if type == "ref" or ( type is None and ID_REGEX.match(value) ):
    return [(None, long(ID_REGEX.match(value).group(1)))]
  elif type == "file_ref" or (type is None and FILE_REF_REGEX.match(value)):
    match = FILE_REF_REGEX.match(value)
    return [("status", long(match.group(1))),
            ("filename", long(match.group(2)))]
  elif type == "struct" or (type is None and STRUCT_START.match(value)):
    result = []
    for match in STRUCT_ELEM.finditer(value):
      k, v = match.groups()
      for k2, v2 in refs_from_val(None, v):
        if k2 is None:
          full_k = k
        else:
          full_k = k + "." + k2
        result.append((full_k, v2))
    return result
  elif type == "container" or (type is None and CONTAINER_START.match(value)):
    result = []
    for match in CONTAINER_ELEM.finditer(value):
      k, v = match.groups()
      for k2, v2 in refs_from_val(None, v):
        if k2 is None:
          full_k = k
        else:
          full_k = k + "." + k2
        result.append((full_k, v2))
    return result
  else:
    return []

def add_in_edges():
  """
  Add in all of the reverse edges in the graph
  """
  for d in datums.itervalues():
    for sub, v in d.references:
      get_datum(v).add_in_edge(d.id)

ALLOC_REGEX = re.compile(r"^([A-Za-z0-9:_]+)=<(\d+)>$")
CREATE_REGEX = re.compile(r"ADLB: Create <(\d+)> t:([a-zA-Z_]+) r:(\d+) w:(\d+)")
CREATE_CONTAINER_REGEX = re.compile(r"ADLB: Create container <(\d+)> k:([a-zA-Z_]+) v:([a-zA-Z_]+)")
LEAK_REGEX = re.compile(r"LEAK DETECTED: <(\d+)> t:([a-zA-Z_]+) r:(\d+) w:(\d+) v:(.+)\n")
REFCOUNT_REGEX = re.compile(r"(read|write)_refcount: <(\d+)> => (\d+)")
GC_REGEX = re.compile(r"datum_gc: <(\d+)>")
DATA_STORE_REGEX = re.compile(r"data_store <(\d+)>(?:\[(.*)\])?=(.*)$")

for line in fileinput.input():
  try:
    if line.find("allocated") >= 0:
      for tok in line.split():
        # use CREATE_REGEX
        match = ALLOC_REGEX.match(tok)
        if match:
          name, id_ = match.groups()
          id_ = long(id_)
          d = get_datum(id_)
          d.name = name
    elif line.find("ADLB: Create <") >= 0:
      match = CREATE_REGEX.search(line)
      if match:
        id_, type_, r, w = match.groups()
        id_ = long(id_)
        r = int(r)
        w = int(w)
        d = get_datum(id_)
        d.type = type_
        d.read_rc = r
        d.write_rc = w
    elif line.find("ADLB: Create container <") >= 0:
      match = CREATE_CONTAINER_REGEX.search(line)
      if match:
        id_, kt, vt = match.groups()
        id_ = long(id_)
        w = int(w)
        d = get_datum(id_)
        d.key_type = kt
        d.val_type = vt
    elif REFCOUNT_REGEX.search(line) is not None:
      rc_type, id, rc = REFCOUNT_REGEX.search(line).groups()
      d = get_datum(long(id))
      if rc_type == "read":
        d.read_rc = int(rc)
      elif rc_type == "write":
        d.write_rc = int(rc)
      else:
        print "Unknown rc type " + rc_type
    elif GC_REGEX.search(line) is not None:
      id = long(GC_REGEX.search(line).group(1))
      d = get_datum(id)
      d.freed = True
    elif DATA_STORE_REGEX.search(line) is not None:
      id, subscript, val = DATA_STORE_REGEX.search(line).groups()
      d = get_datum(long(id))
      if subscript is None:
        d.set_value(val)
      else:
        d.add_subscript_value(subscript, val)
        
    elif line.find("LEAK DETECTED: ") >= 0:
      #TODO: add to leakcheck type, refcounts, value, 
      match = LEAK_REGEX.search(line)
      if match:
        id_, type_, r, w, val = match.groups()
        id_ = long(id_)
        r = int(r)
        w = int(w)
      
        d = get_datum(id_)
        d.type = type_
        d.set_value(val)
        d.leaked = True
        d.read_rc = r
        d.write_rc = w
    # TODO: add in additional info from stores, etc to help debug crashes
    #  -> store
    #  -> refcount
    #  -> add in pending rules
  except Exception, e:
    print e
    print "Error parsing line: " + line

add_in_edges()


def print_graph():
  for id, datum in sorted(datums.items()):
    print "<%ld>(\"%s\", %s) => %s" % (
          datum.id, datum.name, datum.type, str(datum.references))

def node_label(datum, show_rc=False):
  label = "<%ld>" % datum.id
  
  if datum.type:
    label += datum.type

  if datum.name:
    label += " '" + datum.name + "'"

  if show_rc:
    if datum.read_rc is not None:
      label += " r=%d" % (datum.read_rc)
    if datum.write_rc is not None:
      label += " w=%d" % (datum.write_rc)
  return label

def node_colour(datum):
  if datum.leaked:
    return "pink"
  if datum.freed:
    return "grey"
  return "white"

def draw_graph(only_leaked=False, show_rc=False, datums_to_render=None):
  # Directed multi-graph
  G = gv.AGraph(strict=False, directed=True)
  G.node_attr['style'] = "filled"

  if datums_to_render is None:
    datums_to_render = datums.values()


  datum_ids = set([d.id for d in datums_to_render])

  for  datum in datums_to_render:
    if only_leaked and not datum.leaked:
      continue

    id = datum.id
    G.add_node(id)
    node = G.get_node(id)
    node.attr['label'] = node_label(datum, show_rc=show_rc)
    node.attr['fillcolor'] = node_colour(datum)

    for edge_name, dst_id in datum.references:
      # only show edges to nodes being rendered
      if dst_id in datum_ids:
        G.add_edge(id, dst_id, edge_name)
        edge = G.get_edge(id, dst_id)
        if edge_name is None:
          edge_name = ""
        edge.attr['label'] = edge_name
  G.layout('circo')
  try:
    G.draw(format="xlib")
  except IOError:
    pass

def get_connected(datum_id, radius=None):
  """
  Return a set of datums connected to datum_id
  """
  to_visit = [(datum_id, 0)]
  visited_ids = set()
  result = set()
  while len(to_visit) > 0:
    id_, distance = to_visit.pop()
    d = get_datum(id_)
    result.add(d)
    if radius is None or distance < radius:
      for sub, id in d.references:
        if id not in visited_ids:
          to_visit.append((id, distance + 1))
          visited_ids.add(id)
      for id in d.in_edges:
        if id not in visited_ids:
          to_visit.append((id, distance + 1))
          visited_ids.add(id)
  return result

show_rc = False
print "Turbine Vis Tool"
print "================"
while True:
  print
  print "Menu:"
  print "l) Leak check view"
  print "d) Full data view"
  print "d <id> [<radius>]) Data view of things connected to <id>, with optional radius"
  print "rc) Enable showing of reference counts"
  print "no-rc) Disable showing of reference counts"
  print "p) Print graph"
  print "q) Quit"
  print "?",
  choice = raw_input().lower()
  choice_toks = choice.split()
  if len(choice_toks) == 1:
    choice = choice_toks[0]
    if choice == "l":
      draw_graph(only_leaked=True, show_rc=True)
    elif choice == "d":
      draw_graph(show_rc=show_rc)
    elif choice == "rc":
      show_rc = True
    elif choice == "no-rc":
      show_rc = False
    elif choice == "p":
      print_graph()
    elif choice == "q":
      break
    else:
      print "Invalid choice '%s'" % choice
  elif choice_toks[0] == "d" and len(choice_toks) in (2, 3):
    try:
      input_id = long(choice_toks[1])
    except ValueError:
      print "Expected integer data id, but got %s" % (choice_toks[1])
      continue
    d = get_datum(input_id, create=False)
    if d is None:
      print "Datum <%ld> not found" % input_id
      continue
    radius = None
    if (len(choice_toks) == 3):
      try:
        radius = int(choice_toks[2])
      except ValueError:
        print "Expected integer radius"
        continue
    datums_to_render = get_connected(input_id, radius=radius)
    draw_graph(show_rc=show_rc, datums_to_render=datums_to_render)
  else:
    print "Invalid choice '%s'" % choice

