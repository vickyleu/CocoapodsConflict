import SwiftUI
import shared
//import ImSDK_Plus

struct ContentView: View {
 	let greet = Greeting().greet() //<<<== pod xcframework will break kotlin impl

	var body: some View {
		Text(greet) //
        
	}
}

struct ContentView_Previews: PreviewProvider {
	static var previews: some View {
		ContentView()
	}
}
