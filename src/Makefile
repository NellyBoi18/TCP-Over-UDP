JC = javac
JFLAGS = -g -O

SRC := $(wildcard *.java)
CLS := $(SRC:.java=.class)

all: $(CLS)

%.class: %.java
	$(JC) $(JFLAGS) $<

clean:
	rm -f *.class