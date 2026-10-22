# App-specific R8 rules. Empty on purpose: kotlinx.serialization ships the rules its serializer lookup needs
# (Companion, INSTANCE, serializer(), $$serializer.descriptor), and AGP's default file keeps the runtime
# annotation, InnerClasses and Signature attributes. Add a rule only for reflection R8 cannot see, and scope
# it to the class that needs it.
