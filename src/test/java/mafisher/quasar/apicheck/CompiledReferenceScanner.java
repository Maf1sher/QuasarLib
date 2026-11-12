package mafisher.quasar.apicheck;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

/** Reads class files and reports engine types, annotations, and strings referenced by them. */
public final class CompiledReferenceScanner {
    private static final String QUASAR_PREFIX = "mafisher/quasar/";

    private CompiledReferenceScanner() {
    }

    public static ScanResult scan(Path classFile) throws IOException {
        ClassReader reader = new ClassReader(Files.readAllBytes(classFile));
        Collector collector = new Collector();
        reader.accept(collector, 0);
        return collector.result();
    }

    public record ScanResult(Set<String> types, Set<String> annotations, Set<String> strings) {
        public ScanResult {
            types = Set.copyOf(types);
            annotations = Set.copyOf(annotations);
            strings = Set.copyOf(strings);
        }
    }

    private static final class Collector extends ClassVisitor {
        private final Set<String> types = new LinkedHashSet<>();
        private final Set<String> annotations = new LinkedHashSet<>();
        private final Set<String> strings = new LinkedHashSet<>();

        private Collector() {
            super(Opcodes.ASM9);
        }

        private ScanResult result() {
            return new ScanResult(this.types, this.annotations, this.strings);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
            addInternalName(name);
            addInternalName(superName);
            if (interfaces != null) {
                for (String interfaceName : interfaces) {
                    addInternalName(interfaceName);
                }
            }
            addSignature(signature);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            addAnnotation(descriptor);
            return new ValueCollector();
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, org.objectweb.asm.TypePath typePath,
            String descriptor, boolean visible) {
            addAnnotation(descriptor);
            return new ValueCollector();
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            addInternalName(name);
            addInternalName(outerName);
        }

        @Override
        public void visitOuterClass(String owner, String name, String descriptor) {
            addInternalName(owner);
            addDescriptor(descriptor);
        }

        @Override
        public void visitNestHost(String nestHost) {
            addInternalName(nestHost);
        }

        @Override
        public void visitNestMember(String nestMember) {
            addInternalName(nestMember);
        }

        @Override
        public void visitPermittedSubclass(String permittedSubclass) {
            addInternalName(permittedSubclass);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature,
            Object value) {
            addDescriptor(descriptor);
            addSignature(signature);
            return new FieldCollector();
        }

        @Override
        public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
            addDescriptor(descriptor);
            addSignature(signature);
            return new RecordCollector();
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
            String[] exceptions) {
            addMethodDescriptor(descriptor);
            addSignature(signature);
            if (exceptions != null) {
                for (String exception : exceptions) {
                    addInternalName(exception);
                }
            }
            return new MethodCollector();
        }

        private void addConstant(Object value) {
            if (value instanceof Type type) {
                addType(type);
            } else if (value instanceof Handle handle) {
                addInternalName(handle.getOwner());
                addDescriptor(handle.getDesc());
            } else if (value instanceof ConstantDynamic dynamic) {
                addDescriptor(dynamic.getDescriptor());
                addConstant(dynamic.getBootstrapMethod());
                for (int index = 0; index < dynamic.getBootstrapMethodArgumentCount(); index++) {
                    addConstant(dynamic.getBootstrapMethodArgument(index));
                }
            } else if (value instanceof String text && text.startsWith("mafisher.quasar.")) {
                this.strings.add(text);
            }
        }

        private void addAnnotation(String descriptor) {
            if (descriptor == null) {
                return;
            }
            Type type = Type.getType(descriptor);
            addType(type);
            this.annotations.add(type.getClassName());
        }

        private void addSignature(String signature) {
            if (signature == null) {
                return;
            }
            new SignatureReader(signature).accept(new TypeSignatureCollector());
        }

        private void addMethodDescriptor(String descriptor) {
            if (descriptor == null) {
                return;
            }
            Type method = Type.getMethodType(descriptor);
            addType(method.getReturnType());
            for (Type argument : method.getArgumentTypes()) {
                addType(argument);
            }
        }

        private void addDescriptor(String descriptor) {
            if (descriptor == null) {
                return;
            }
            addType(Type.getType(descriptor));
        }

        private void addType(Type type) {
            if (type == null) {
                return;
            }
            switch (type.getSort()) {
                case Type.ARRAY -> addType(type.getElementType());
                case Type.OBJECT -> addInternalName(type.getInternalName());
                case Type.METHOD -> {
                    addType(type.getReturnType());
                    for (Type argument : type.getArgumentTypes()) {
                        addType(argument);
                    }
                }
                default -> {
                }
            }
        }

        private void addInternalName(String internalName) {
            if (internalName == null) {
                return;
            }
            int arrayStart = 0;
            while (arrayStart < internalName.length() && internalName.charAt(arrayStart) == '[') {
                arrayStart++;
            }
            String candidate = internalName.substring(arrayStart);
            if (candidate.startsWith(QUASAR_PREFIX)) {
                this.types.add(candidate.replace('/', '.'));
            }
        }

        private final class ValueCollector extends AnnotationVisitor {
            private ValueCollector() {
                super(Opcodes.ASM9);
            }

            @Override
            public void visit(String name, Object value) {
                addConstant(value);
            }

            @Override
            public void visitEnum(String name, String descriptor, String value) {
                addAnnotation(descriptor);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                addAnnotation(descriptor);
                return new ValueCollector();
            }

            @Override
            public AnnotationVisitor visitArray(String name) {
                return new ValueCollector();
            }
        }

        private final class TypeSignatureCollector extends SignatureVisitor {
            private final StringBuilder current = new StringBuilder();

            private TypeSignatureCollector() {
                super(Opcodes.ASM9);
            }

            @Override
            public void visitClassType(String name) {
                this.current.setLength(0);
                this.current.append(name);
                addInternalName(name);
            }

            @Override
            public void visitInnerClassType(String name) {
                this.current.append('$').append(name);
                addInternalName(this.current.toString());
            }
        }

        private final class FieldCollector extends FieldVisitor {
            private FieldCollector() {
                super(Opcodes.ASM9);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                addAnnotation(descriptor);
                return new ValueCollector();
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, org.objectweb.asm.TypePath typePath,
                String descriptor, boolean visible) {
                addAnnotation(descriptor);
                return new ValueCollector();
            }
        }

        private final class RecordCollector extends RecordComponentVisitor {
            private RecordCollector() {
                super(Opcodes.ASM9);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                addAnnotation(descriptor);
                return new ValueCollector();
            }
        }

        private final class MethodCollector extends MethodVisitor {
            private MethodCollector() {
                super(Opcodes.ASM9);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                addAnnotation(descriptor);
                return new ValueCollector();
            }

            @Override
            public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                addAnnotation(descriptor);
                return new ValueCollector();
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                addInternalName(type);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                addInternalName(owner);
                addDescriptor(descriptor);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                boolean isInterface) {
                addInternalName(owner);
                addMethodDescriptor(descriptor);
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle,
                Object... bootstrapMethodArguments) {
                addMethodDescriptor(descriptor);
                addConstant(bootstrapMethodHandle);
                for (Object argument : bootstrapMethodArguments) {
                    addConstant(argument);
                }
            }

            @Override
            public void visitLdcInsn(Object value) {
                addConstant(value);
            }

            @Override
            public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                addDescriptor(descriptor);
            }

            @Override
            public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                addInternalName(type);
            }

            @Override
            public void visitLocalVariable(String name, String descriptor, String signature,
                Label start, Label end, int index) {
                addDescriptor(descriptor);
                addSignature(signature);
            }
        }
    }
}
