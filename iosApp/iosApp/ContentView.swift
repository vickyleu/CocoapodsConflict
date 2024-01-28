import SwiftUI
import shared
// import ImSDK_Plus
struct ContentView: View {
	let greet = Greeting().greet()

	var body: some View {
		Text("123") //greet
        
	}
}

struct ContentView_Previews: PreviewProvider {
	static var previews: some View {
		ContentView()
	}
}
