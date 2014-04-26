// The classes are basically the same as the BinaryTree 
// file except the visitor classes and the accept method
// in the Tree class

class MainClass{
    public static void main(String[] a){
	System.out.println(new Grandchild().getInt());
    }
}

class Parent{

	int a;
	int b;
	
	public int getInt() {
		a = 3;
		return 1;
	}
	
	public boolean getBool() {
		return false;
	}
	
	public int getInt2() {
		return this.getInt();
	}
}

class Child extends Parent {

	int c;
	int d;
	int e;

	public int getInt() {
		a = 2;
		return 12;
	}
	
	public int getInt3() {
		e = 1;
		return 13;
	}
}

class Grandchild extends Child {
	
	int f;
	
	public boolean getBool2() {
		b = 2;
		return (this.getInt() > 5);
	}
	
	public boolean getBool() {
		f = 9;
		return true;
	}
}